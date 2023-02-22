/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.namesrv;

import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.rocketmq.common.Configuration;
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.future.FutureTaskExt;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.namesrv.kvconfig.KVConfigManager;
import org.apache.rocketmq.namesrv.processor.ClientRequestProcessor;
import org.apache.rocketmq.namesrv.processor.ClusterTestRequestProcessor;
import org.apache.rocketmq.namesrv.processor.DefaultRequestProcessor;
import org.apache.rocketmq.namesrv.route.ZoneRouteRPCHook;
import org.apache.rocketmq.namesrv.routeinfo.BrokerHousekeepingService;
import org.apache.rocketmq.namesrv.routeinfo.RouteInfoManager;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.RemotingServer;
import org.apache.rocketmq.remoting.common.RemotingUtil;
import org.apache.rocketmq.remoting.common.TlsMode;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.netty.NettyRemotingServer;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.netty.RequestTask;
import org.apache.rocketmq.remoting.netty.TlsSystemConfig;
import org.apache.rocketmq.srvutil.FileWatchService;

public class NamesrvController {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);
    private static final InternalLogger WATER_MARK_LOG = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_WATER_MARK_LOGGER_NAME);

    private final NamesrvConfig namesrvConfig;

    private final NettyServerConfig nettyServerConfig;
    private final NettyClientConfig nettyClientConfig;

    private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1,
            new BasicThreadFactory.Builder().namingPattern("NSScheduledThread").daemon(true).build());

    private final ScheduledExecutorService scanExecutorService = new ScheduledThreadPoolExecutor(1,
            new BasicThreadFactory.Builder().namingPattern("NSScanScheduledThread").daemon(true).build());

    private final KVConfigManager kvConfigManager;
    private final RouteInfoManager routeInfoManager;

    private RemotingClient remotingClient;
    private RemotingServer remotingServer;

    private final BrokerHousekeepingService brokerHousekeepingService;

    private ExecutorService defaultExecutor;
    private ExecutorService clientRequestExecutor;

    private BlockingQueue<Runnable> defaultThreadPoolQueue;
    private BlockingQueue<Runnable> clientRequestThreadPoolQueue;

    private final Configuration configuration;
    private FileWatchService fileWatchService;

    public NamesrvController(NamesrvConfig namesrvConfig, NettyServerConfig nettyServerConfig) {
        this(namesrvConfig, nettyServerConfig, new NettyClientConfig());
    }

    public NamesrvController(NamesrvConfig namesrvConfig, NettyServerConfig nettyServerConfig, NettyClientConfig nettyClientConfig) {
        this.namesrvConfig = namesrvConfig;
        this.nettyServerConfig = nettyServerConfig;
        this.nettyClientConfig = nettyClientConfig;
        this.kvConfigManager = new KVConfigManager(this);
        this.brokerHousekeepingService = new BrokerHousekeepingService(this);
        this.routeInfoManager = new RouteInfoManager(namesrvConfig, this);
        this.configuration = new Configuration(LOGGER, this.namesrvConfig, this.nettyServerConfig);
        this.configuration.setStorePathFromConfig(this.namesrvConfig, "configStorePath");
    }

    public boolean initialize() {
        // 加载配置
        loadConfig();
        // 初始化网络组件
        initiateNetworkComponents();
        // 初始化线程执行器
        initiateThreadExecutors();
        // 注册处理器 请求的处理器
        registerProcessor();
        // 开始调度任务服务
        startScheduleService();
        // 初始化Ssl上下文
        // SSL（Secure Socket Layer）安全套接层是Netscape公司率先采用的网络安全协议。它是在传输通信协议（TCP/IP）上实现的一种安全协议，采用公开密钥技术。SSL广泛支持各种类型的网络，同时提供三种基本的安全服务，它们都使用公开密钥技术。
        initiateSslContext();
        // 初始化Rpc钩子
        initiateRpcHooks();
        return true;
    }

    private void loadConfig() {
        // 键值对配置管理器 加载
        this.kvConfigManager.load();
    }

    private void startScheduleService() {
        // 扫描执行器服务 调度任务 固定频率
        // 任务 namesrc控制器 路由信息管理器 扫描不活跃broker 即下线的Broker
        // 延迟5秒后第一次执行
        // 以后每5秒执行扫描不活跃Broker
        // 时间单位 毫秒
        // 具体Broker的信息，每个Broker会定期向Namesrc汇报，不是Namesrc主动去拉取Broker信息
        this.scanExecutorService.scheduleAtFixedRate(NamesrvController.this.routeInfoManager::scanNotActiveBroker,
                                                     5,
                                                     this.namesrvConfig.getScanNotActiveBrokerInterval(),
                                                     TimeUnit.MILLISECONDS);
        // 能调度的执行器服务 输出所有配置周期性 kvConfigManger
        // 延迟1分钟后第一次执行，以后每10分钟执行一次
        this.scheduledExecutorService.scheduleAtFixedRate(NamesrvController.this.kvConfigManager::printAllPeriodically,
            1, 10, TimeUnit.MINUTES);
        // NamesrvController 输出水印/水位标记
        // 延迟10秒后第一次执行 以后每1秒执行一次
        this.scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                NamesrvController.this.printWaterMark();
            } catch (Throwable e) {
                LOGGER.error("printWaterMark error.", e);
            }
        }, 10, 1, TimeUnit.SECONDS);
    }

    private void initiateNetworkComponents() {
        // 远程服务端 等于 新的 netty 远程 服务器 netty 服务器配置 broker家保持服务
        this.remotingServer = new NettyRemotingServer(this.nettyServerConfig, this.brokerHousekeepingService);
        // 远程客户端 等于 新的 netty 远程 客户端 netty 客户端配置
        this.remotingClient = new NettyRemotingClient(this.nettyClientConfig);
    }

    private void initiateThreadExecutors() {
        // 默认线程池队列 有链表的阻塞队列 namesrc配置 获取默认线程池队列容量
        this.defaultThreadPoolQueue = new LinkedBlockingQueue<>(this.namesrvConfig.getDefaultThreadPoolQueueCapacity());
        // 默认执行器 线程池执行器 namesrc配置 获取默认线程池数量
        // 核心池数量 最大池数量 保持活跃事件 60秒 事件单位 毫秒
        this.defaultExecutor = new ThreadPoolExecutor(this.namesrvConfig.getDefaultThreadPoolNums(),
                                                      this.namesrvConfig.getDefaultThreadPoolNums(),
                                                      1000 * 60,
                                                      TimeUnit.MILLISECONDS,
                                                      this.defaultThreadPoolQueue,
                                                      new ThreadFactoryImpl("RemotingExecutorThread_")) {
            @Override
            protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
                return new FutureTaskExt<>(runnable, value);
            }
        };
        // 客户端请求线程池队列
        this.clientRequestThreadPoolQueue = new LinkedBlockingQueue<>(this.namesrvConfig.getClientRequestThreadPoolQueueCapacity());
        // Java util 并发 线程池执行器
        // volatile adjective /ˈvɒl.ə.taɪl/ 不稳定的；易变的；易怒的，喜怒无常的
        // volatile是Java提供的一种轻量级的同步机制。Java 语言包含两种内在的同步机制：同步块（或方法）和 volatile 变量，相比于synchronized（synchronized通常称为重量级锁），volatile更轻量级，因为它不会引起线程上下文的切换和调度。
        this.clientRequestExecutor = new ThreadPoolExecutor(this.namesrvConfig.getClientRequestThreadPoolNums(),
                                                            this.namesrvConfig.getClientRequestThreadPoolNums(),
                                                            1000 * 60,
                                                            TimeUnit.MILLISECONDS,
                                                            this.clientRequestThreadPoolQueue,
                                                            new ThreadFactoryImpl("ClientRequestExecutorThread_")) {
            @Override
            protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
                return new FutureTaskExt<>(runnable, value);
            }
        };
    }

    private void initiateSslContext() {
        if (TlsSystemConfig.tlsMode == TlsMode.DISABLED) {
            return;
        }

        String[] watchFiles = {TlsSystemConfig.tlsServerCertPath, TlsSystemConfig.tlsServerKeyPath, TlsSystemConfig.tlsServerTrustCertPath};

        FileWatchService.Listener listener = new FileWatchService.Listener() {
            boolean certChanged, keyChanged = false;

            @Override
            public void onChanged(String path) {
                if (path.equals(TlsSystemConfig.tlsServerTrustCertPath)) {
                    LOGGER.info("The trust certificate changed, reload the ssl context");
                    ((NettyRemotingServer) remotingServer).loadSslContext();
                }
                if (path.equals(TlsSystemConfig.tlsServerCertPath)) {
                    certChanged = true;
                }
                if (path.equals(TlsSystemConfig.tlsServerKeyPath)) {
                    keyChanged = true;
                }
                if (certChanged && keyChanged) {
                    LOGGER.info("The certificate and private key changed, reload the ssl context");
                    certChanged = keyChanged = false;
                    ((NettyRemotingServer) remotingServer).loadSslContext();
                }
            }
        };

        try {
            fileWatchService = new FileWatchService(watchFiles, listener);
        } catch (Exception e) {
            LOGGER.warn("FileWatchService created error, can't load the certificate dynamically");
        }
    }

    private void printWaterMark() {
        // [水印/水位标记] a mark showing the highest or lowest level that a river or the sea reaches
        // 客户端队列大小 客户端队列慢时间 默认对垒大小 默认队列慢时间
        WATER_MARK_LOG.info("[WATERMARK] ClientQueueSize:{} ClientQueueSlowTime:{} " + "DefaultQueueSize:{} DefaultQueueSlowTime:{}",
                            this.clientRequestThreadPoolQueue.size(),
                            headSlowTimeMills(this.clientRequestThreadPoolQueue),
                            this.defaultThreadPoolQueue.size(),
                            headSlowTimeMills(this.defaultThreadPoolQueue));
    }

    private long headSlowTimeMills(BlockingQueue<Runnable> q) {
        long slowTimeMills = 0;
        final Runnable firstRunnable = q.peek();

        if (firstRunnable instanceof FutureTaskExt) {
            final Runnable inner = ((FutureTaskExt<?>) firstRunnable).getRunnable();
            if (inner instanceof RequestTask) {
                slowTimeMills = System.currentTimeMillis() - ((RequestTask) inner).getCreateTimestamp();
            }
        }

        if (slowTimeMills < 0) {
            slowTimeMills = 0;
        }

        return slowTimeMills;
    }

    private void registerProcessor() {
        // namesrc配置 是否是集群测试
        if (namesrvConfig.isClusterTest()) {

            this.remotingServer.registerDefaultProcessor(new ClusterTestRequestProcessor(this, namesrvConfig.getProductEnvName()), this.defaultExecutor);
        } else {
            // Support get route info only temporarily
            ClientRequestProcessor clientRequestProcessor = new ClientRequestProcessor(this);
            // 远程服务端 注册处理器 请求码 获取路由信息 通过 主题
            this.remotingServer.registerProcessor(RequestCode.GET_ROUTEINFO_BY_TOPIC,
                                                  clientRequestProcessor,
                                                  this.clientRequestExecutor);
            // 注册默认处理器
            this.remotingServer.registerDefaultProcessor(new DefaultRequestProcessor(this),
                                                         this.defaultExecutor);
        }
    }

    private void initiateRpcHooks() {
        // 区域路由RPC钩子
        this.remotingServer.registerRPCHook(new ZoneRouteRPCHook());
    }

    public void start() throws Exception {
        // 远程服务器 开始
        this.remotingServer.start();

        // In test scenarios where it is up to OS to pick up an available port, set the listening port back to config
        // 在由OS选择可用端口的测试场景中，将侦听端口设置回config
        // bindAddress="0.0.0.0"
        // listenPort=9876
        if (0 == nettyServerConfig.getListenPort()) {
            nettyServerConfig.setListenPort(this.remotingServer.localListenPort());
        }
        // 远程客户端 更新NameServer地址列表
        // NettyRemotingClient
        // namesrvAddrList = [192.168.1.103:9876]
        // 远程工具 获取本机地址
        this.remotingClient.updateNameServerAddressList(Collections.singletonList(RemotingUtil.getLocalAddress()
            + ":" + nettyServerConfig.getListenPort()));
        // 远程客户端 启动
        this.remotingClient.start();
        // 如果文件 监控 服务 不为空
        // 备注：实际有启动
        if (this.fileWatchService != null) {
            // 文件 监控 服务 启动
            this.fileWatchService.start();
        }
        // 路由信息管理器 启动
        this.routeInfoManager.start();
    }

    public void shutdown() {
        this.remotingClient.shutdown();
        this.remotingServer.shutdown();
        this.defaultExecutor.shutdown();
        this.clientRequestExecutor.shutdown();
        this.scheduledExecutorService.shutdown();
        this.scanExecutorService.shutdown();
        this.routeInfoManager.shutdown();

        if (this.fileWatchService != null) {
            this.fileWatchService.shutdown();
        }
    }

    public NamesrvConfig getNamesrvConfig() {
        return namesrvConfig;
    }

    public NettyServerConfig getNettyServerConfig() {
        return nettyServerConfig;
    }

    public KVConfigManager getKvConfigManager() {
        return kvConfigManager;
    }

    public RouteInfoManager getRouteInfoManager() {
        return routeInfoManager;
    }

    public RemotingServer getRemotingServer() {
        return remotingServer;
    }

    public RemotingClient getRemotingClient() {
        return remotingClient;
    }

    public void setRemotingServer(RemotingServer remotingServer) {
        this.remotingServer = remotingServer;
    }

    public Configuration getConfiguration() {
        return configuration;
    }
}
