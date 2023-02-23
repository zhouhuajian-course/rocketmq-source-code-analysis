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
package org.apache.rocketmq.broker;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.remoting.common.RemotingUtil;
import org.apache.rocketmq.remoting.common.TlsMode;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.netty.TlsSystemConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.srvutil.ServerUtil;
import org.apache.rocketmq.store.config.BrokerRole;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.rocketmq.remoting.netty.TlsSystemConfig.TLS_ENABLE;

// broker Broker启动类
public class BrokerStartup {
    public static Properties properties = null;
    public static CommandLine commandLine = null;
    public static String configFile = null;
    public static InternalLogger log;
    public static final SystemConfigFileHelper CONFIG_FILE_HELPER = new SystemConfigFileHelper();
    // 入口函数
    public static void main(String[] args) {
        // 启动 创建Broker控制器 args
        start(createBrokerController(args));
    }

    public static BrokerController start(BrokerController controller) {
        try {

            controller.start();

            String tip = "The broker[" + controller.getBrokerConfig().getBrokerName() + ", "
                + controller.getBrokerAddr() + "] boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();

            if (null != controller.getBrokerConfig().getNamesrvAddr()) {
                tip += " and name server is " + controller.getBrokerConfig().getNamesrvAddr();
            }

            log.info(tip);
            System.out.printf("%s%n", tip);
            return controller;
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }

        return null;
    }

    public static void shutdown(final BrokerController controller) {
        if (null != controller) {
            controller.shutdown();
        }
    }

    public static BrokerController createBrokerController(String[] args) {
        // 系统 设置属性 远程命令 远程版本键 MQ当前版本
        System.setProperty(RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));

        try {
            // 包冲突检测 检测 Fastjson
            //PackageConflictDetect.detectFastjson();
            // 服务端工具 构建命令行选项 新选项 commons cli
            Options options = ServerUtil.buildCommandlineOptions(new Options());
            // 服务单工具 解析命令行 appName mqbroker 参数 默认解析器
            commandLine = ServerUtil.parseCmdLine("mqbroker", args, buildCommandlineOptions(options),
                new DefaultParser());
            // 命令行对象 为 空
            if (null == commandLine) {
                // 系统 退出 状态码 -1
                System.exit(-1);
            }
            // Broker配置
            final BrokerConfig brokerConfig = new BrokerConfig();
            // netty 服务端配置
            final NettyServerConfig nettyServerConfig = new NettyServerConfig();
            // netty 客户端配置
            final NettyClientConfig nettyClientConfig = new NettyClientConfig();
            // netty 客户端配置 设置使用TLS
            nettyClientConfig.setUseTLS(Boolean.parseBoolean(System.getProperty(TLS_ENABLE,
                String.valueOf(TlsSystemConfig.tlsMode == TlsMode.ENFORCING))));
            // netty 服务端配置 设置监听端口 10911
            nettyServerConfig.setListenPort(10911);
            // 消息存储配置
            final MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
            // 如果消息存储配置获取broker角色 是 从角色
            if (BrokerRole.SLAVE == messageStoreConfig.getBrokerRole()) {
                // ratio noun [ C ] UK  /ˈreɪ.ʃi.əʊ/
                // plural ratios 比；比例；比率
                // 消息存储配置 获取访问消息 在内存 最大比例 -10
                int ratio = messageStoreConfig.getAccessMessageInMemoryMaxRatio() - 10;
                // 设置访问消息 在内存 最大比例
                messageStoreConfig.setAccessMessageInMemoryMaxRatio(ratio);
            }

            // 是否 命令行 有选项 c
            if (commandLine.hasOption('c')) {
                String file = commandLine.getOptionValue('c');
                if (file != null) {
                    CONFIG_FILE_HELPER.setFile(file);
                    configFile = file;
                    BrokerPathConfigHelper.setBrokerConfigPath(file);
                    // 加载配置
                    properties = CONFIG_FILE_HELPER.loadConfig();
                }
            }

            if (properties != null) {
                // 属性转 系统环境
                properties2SystemEnv(properties);
                // 属性转对象 属性 broker配置 netty 服务端 客户端配置 消息存储配置
                MixAll.properties2Object(properties, brokerConfig);
                MixAll.properties2Object(properties, nettyServerConfig);
                MixAll.properties2Object(properties, nettyClientConfig);
                MixAll.properties2Object(properties, messageStoreConfig);
            }

            MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), brokerConfig);
            // ROCKETMQ_HOME 安装目录
            if (null == brokerConfig.getRocketmqHome()) {
                System.out.printf("Please set the %s variable in your environment to match the location of the RocketMQ installation", MixAll.ROCKETMQ_HOME_ENV);
                System.exit(-2);
            }
            // broker配置 获取namesrc地址 获取rocketmq.namesrv.addr默认 环境变量 NAMESRV_ADDR
            String namesrvAddr = brokerConfig.getNamesrvAddr();
            // 有namesrc地址
            if (null != namesrvAddr) {
                try {
                    // namesrc地址 分割 分号
                    String[] addrArray = namesrvAddr.split(";");
                    for (String addr : addrArray) {
                        // 远程工具 字符串 转 socket地址 为了检测地址是否合法
                        RemotingUtil.string2SocketAddress(addr);
                    }
                } catch (Exception e) {
                    System.out.printf(
                        "The Name Server Address[%s] illegal, please set it as follows, \"127.0.0.1:9876;192.168.0.1:9876\"%n",
                        namesrvAddr);
                    System.exit(-3);
                }
            }
            // broker配置 是否启用控制器模式 如果不启用
            if (!brokerConfig.isEnableControllerMode()) {
                // broker角色
                switch (messageStoreConfig.getBrokerRole()) {
                    case ASYNC_MASTER:
                    case SYNC_MASTER:
                        // broker配置 设置BrokerId
                        // public static final long MASTER_ID = 0L;
                        brokerConfig.setBrokerId(MixAll.MASTER_ID);
                        break;
                    case SLAVE:
                        // broker配置 获取BrokerId 如果 小于或等于 0
                        if (brokerConfig.getBrokerId() <= 0) {
                            // slave的brokerID必须 大于0
                            System.out.printf("Slave's brokerId must be > 0");
                            System.exit(-3);
                        }

                        break;
                    default:
                        break;
                }
            }
            // 消息存储配置 是否启动DLeger Commit log
            // 默认false
            if (messageStoreConfig.isEnableDLegerCommitLog()) {
                // broker配置 设置BrokerId -1
                brokerConfig.setBrokerId(-1);
            }
            // 消息存储配置 设置高可用监听端口 netty 服务端配置 获取监听端口 加+
            messageStoreConfig.setHaListenPort(nettyServerConfig.getListenPort() + 1);
            // 日志配置文件
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(lc);
            lc.reset();
            System.setProperty("brokerLogDir", "");
            // isolate verb [ T ] UK  /ˈaɪ.sə.leɪt/
            // to separate something from other things with which it is connected or mixed
            // 隔离；孤立；分离
            // 是否分离的日志可用 默认false
            if (brokerConfig.isIsolateLogEnable()) {
                // 日志目录 BrokerName_BrokeID
                System.setProperty("brokerLogDir", brokerConfig.getBrokerName() + "_" + brokerConfig.getBrokerId());
            }
            // 是否分离日志可用 默认false 是否开启DLeger提交记录
            if (brokerConfig.isIsolateLogEnable() && messageStoreConfig.isEnableDLegerCommitLog()) {
                // 日志目录会有区分
                System.setProperty("brokerLogDir", brokerConfig.getBrokerName() + "_" + messageStoreConfig.getdLegerSelfId());
            }
            // 配置器 做配置 RMQ 家 /conf/logback_broker.xml
            configurator.doConfigure(brokerConfig.getRocketmqHome() + "/conf/logback_broker.xml");
            // 是否 命令行 有选项 p
            if (commandLine.hasOption('p')) {
                InternalLogger console = InternalLoggerFactory.getLogger(LoggerName.BROKER_CONSOLE_NAME);
                MixAll.printObjectProperties(console, brokerConfig);
                MixAll.printObjectProperties(console, nettyServerConfig);
                MixAll.printObjectProperties(console, nettyClientConfig);
                MixAll.printObjectProperties(console, messageStoreConfig);
                System.exit(0);
                // only important field
            } else if (commandLine.hasOption('m')) {
                InternalLogger console = InternalLoggerFactory.getLogger(LoggerName.BROKER_CONSOLE_NAME);
                MixAll.printObjectProperties(console, brokerConfig, true);
                MixAll.printObjectProperties(console, nettyServerConfig, true);
                MixAll.printObjectProperties(console, nettyClientConfig, true);
                MixAll.printObjectProperties(console, messageStoreConfig, true);
                System.exit(0);
            }

            log = InternalLoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
            MixAll.printObjectProperties(log, brokerConfig);
            MixAll.printObjectProperties(log, nettyServerConfig);
            MixAll.printObjectProperties(log, nettyClientConfig);
            MixAll.printObjectProperties(log, messageStoreConfig);
            // broker config set in broker container
            brokerConfig.setInBrokerContainer(false);
            // broker controller
            final BrokerController controller = new BrokerController(
                brokerConfig,
                nettyServerConfig,
                nettyClientConfig,
                messageStoreConfig);
            // remember all configs to prevent discard
            controller.getConfiguration().registerConfig(properties);
            // controller initialize
            boolean initResult = controller.initialize();
            if (!initResult) {
                controller.shutdown();
                System.exit(-3);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                private volatile boolean hasShutdown = false;
                private AtomicInteger shutdownTimes = new AtomicInteger(0);

                @Override
                public void run() {
                    synchronized (this) {
                        log.info("Shutdown hook was invoked, {}", this.shutdownTimes.incrementAndGet());
                        if (!this.hasShutdown) {
                            this.hasShutdown = true;
                            long beginTime = System.currentTimeMillis();
                            controller.shutdown();
                            long consumingTimeTotal = System.currentTimeMillis() - beginTime;
                            log.info("Shutdown hook over, consuming total time(ms): {}", consumingTimeTotal);
                        }
                    }
                }
            }, "ShutdownHook"));

            return controller;
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }

        return null;
    }

    private static void properties2SystemEnv(Properties properties) {
        if (properties == null) {
            return;
        }
        // rmq 地址 服务器 域名 默认MixAll.WS_DOMAIN_NAME
        String rmqAddressServerDomain = properties.getProperty("rmqAddressServerDomain", MixAll.WS_DOMAIN_NAME);
        // rmq 地址服务器子组 默认MixAll.WS_DOMAIN_SUBGROUP
        String rmqAddressServerSubGroup = properties.getProperty("rmqAddressServerSubGroup", MixAll.WS_DOMAIN_SUBGROUP);
        System.setProperty("rocketmq.namesrv.domain", rmqAddressServerDomain);
        System.setProperty("rocketmq.namesrv.domain.subgroup", rmqAddressServerSubGroup);
    }

    private static Options buildCommandlineOptions(final Options options) {
        Option opt = new Option("c", "configFile", true, "Broker config properties file");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p", "printConfigItem", false, "Print all config item");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("m", "printImportantConfig", false, "Print important config item");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }

    public static class SystemConfigFileHelper {
        private static final Logger LOGGER = LoggerFactory.getLogger(SystemConfigFileHelper.class);

        private String file;

        public SystemConfigFileHelper() {
        }

        public Properties loadConfig() throws Exception {
            InputStream in = new BufferedInputStream(new FileInputStream(file));
            Properties properties = new Properties();
            properties.load(in);
            in.close();
            return properties;
        }

        public void update(Properties properties) throws Exception {
            LOGGER.error("[SystemConfigFileHelper] update no thing.");
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getFile() {
            return file;
        }
    }
}
