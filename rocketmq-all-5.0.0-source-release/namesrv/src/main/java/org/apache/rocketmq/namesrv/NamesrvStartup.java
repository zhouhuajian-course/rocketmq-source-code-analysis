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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Callable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.ControllerConfig;
import org.apache.rocketmq.controller.ControllerManager;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.srvutil.ServerUtil;
import org.apache.rocketmq.srvutil.ShutdownHookThread;
import org.slf4j.LoggerFactory;

public class NamesrvStartup {
    // 内部日志实例
    private static InternalLogger log;
    // 属性
    private static Properties properties = null;
    // namesrv配置
    private static NamesrvConfig namesrvConfig = null;
    // Netty 服务端配置
    private static NettyServerConfig nettyServerConfig = null;
    // Netty 客户端配置
    private static NettyClientConfig nettyClientConfig = null;
    // 控制器配置
    private static ControllerConfig controllerConfig = null;

    public static void main(String[] args) {
        main0(args);
        // 控制器管理者
        controllerManagerMain();
    }

    public static void main0(String[] args) {
        try {
            // 解析命令行和配置文件 args为空字符串数组 args=[]
            parseCommandlineAndConfigFile(args);
            // 创建并启动namesrv控制器
            createAndStartNamesrvController();
        } catch (Throwable e) {
            // 启动出现异常，打印调用栈追踪
            e.printStackTrace();
            // 系统退出 退出状态码为-1
            System.exit(-1);
        }

    }

    public static void controllerManagerMain() {
        try {
            if (namesrvConfig.isEnableControllerInNamesrv()) {
                createAndStartControllerManager();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static void parseCommandlineAndConfigFile(String[] args) throws Exception {
        // 系统设置属性
        // 远程版本键
        // public static final String REMOTING_VERSION_KEY = "rocketmq.remoting.version";
        // Integer.toString(MQVersion.CURRENT_VERSION) "413"
        System.setProperty(RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
        //PackageConflictDetect.detectFastjson();
        // 服务端工具 构建命令行选项  Options 选项 cli命令行交互 选项
        Options options = ServerUtil.buildCommandlineOptions(new Options());
        // 服务端工具 解析命令行  应用名 mqnamesrv 参数 args 构建命令行选项 options 选项解析器 PosixParser
        CommandLine commandLine = ServerUtil.parseCmdLine("mqnamesrv",
                                                          args,
                                                          buildCommandlineOptions(options),
                                                          new PosixParser());
        // 如果 空 等于 命令行实例
        if (null == commandLine) {
            // 系统 退出 状态码-1
            System.exit(-1);
            return;
        }

        // namesrv配置
        namesrvConfig = new NamesrvConfig();
        // netty 服务端配置
        nettyServerConfig = new NettyServerConfig();
        // netty 客户端配置
        nettyClientConfig = new NettyClientConfig();
        // netty 服务端配置 设置监听端口 9876
        nettyServerConfig.setListenPort(9876);
        // 控制器配置
        controllerConfig = new ControllerConfig();
        // 如果命令行有选项 c
        if (commandLine.hasOption('c')) {
            // 命令行获取选项值 c
            // 配置文件路径
            String file = commandLine.getOptionValue('c');
            // 文件路径不等于null
            if (file != null) {
                // 有缓冲区的输入流实例
                // java.nio.file.Files Paths 非阻塞IO
                InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(file)));
                // 属性实例
                properties = new Properties();
                // 属性 加载 输入流实例
                properties.load(in);
                // 属性 转 对象 属性 namesrv配置
                // 属性值 设置为对象属性 如果存在
                MixAll.properties2Object(properties, namesrvConfig);
                MixAll.properties2Object(properties, nettyServerConfig);
                MixAll.properties2Object(properties, nettyClientConfig);
                MixAll.properties2Object(properties, controllerConfig);
                // namesrv配置 设置配置存放路径 file
                namesrvConfig.setConfigStorePath(file);
                // 系统 标准输出 格式化输出 加载配置属性文件成功 文件名 换行
                System.out.printf("load config properties file OK, %s%n", file);
                // 输入流 关闭
                in.close();
            }
        }

        // 如果命令行 有选项 p
        if (commandLine.hasOption('p')) {
            // 混合所有 输出对象属性 日志记录器 null
            MixAll.printObjectProperties(null, namesrvConfig);
            MixAll.printObjectProperties(null, nettyServerConfig);
            MixAll.printObjectProperties(null, nettyClientConfig);
            MixAll.printObjectProperties(null, controllerConfig);
            System.exit(0);
        }

        MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), namesrvConfig);
        // 如果 空 等于 namesrv配置 获取rocketmq家
        if (null == namesrvConfig.getRocketmqHome()) {
            // 设置 ROCKETMQ_HOME 环境变量
            // 匹配 RocketMQ 安装位置
            System.out.printf("Please set the %s variable in your environment to match the location of the RocketMQ installation%n", MixAll.ROCKETMQ_HOME_ENV);
            System.exit(-2);
        }
        // slf4j 简单日志门面 for Java 日志器工厂 获取ILoggerFactory
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // ch\qos\logback\classic\joran\JoranConfigurator.class
        // logback 经典 joran joran配置器
        // Joran, logback's configuration system
        JoranConfigurator configurator = new JoranConfigurator();
        // 配置器 设置上下文 日志上下文
        configurator.setContext(lc);
        // 日志上下文 重置
        lc.reset();
        // 配置器 做配置 文件名 namesrv配置 获取rocketmq家 + /conf/logback_namesrv.xml
        configurator.doConfigure(namesrvConfig.getRocketmqHome() + "/conf/logback_namesrv.xml");
        // 内部日志工厂 获取日志器 日志器名
        log = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);
        // 输出对象属性 namesrc配置
        MixAll.printObjectProperties(log, namesrvConfig);
        // 输出对象属性 netty服务端配置
        MixAll.printObjectProperties(log, nettyServerConfig);
    }

    public static void createAndStartNamesrvController() throws Exception {
        // 创建namesrc控制器
        NamesrvController controller = createNamesrvController();
        // 启动控制器
        start(controller);
        // 提示 name server 启动 成功 序列化类型 等于 远程命令 获取序列化类型配置 在这个服务器
        // JSON
        String tip = "The Name Server boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();
        // 日志 信息 提示
        log.info(tip);
        // 系统 标准输出 格式化打印 提示 换行
        System.out.printf("%s%n", tip);
    }

    public static NamesrvController createNamesrvController() {
        // namesrc控制器 namesrc配置 netty服务端配置 netty客户端配置
        final NamesrvController controller = new NamesrvController(namesrvConfig, nettyServerConfig, nettyClientConfig);
        // remember all configs to prevent discard
        // 控制器 获取配置 注册配置 namesrc.properties 配置
        controller.getConfiguration().registerConfig(properties);
        // 返回控制器
        return controller;
    }

    public static NamesrvController start(final NamesrvController controller) throws Exception {
        // 如果 空 等于 控制器对象
        if (null == controller) {
            // 抛出 非法参数异常
            throw new IllegalArgumentException("NamesrvController is null");
        }
        // 控制器 初始化
        boolean initResult = controller.initialize();
        if (!initResult) {
            controller.shutdown();
            System.exit(-3);
        }

        Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(log, (Callable<Void>) () -> {
            controller.shutdown();
            return null;
        }));

        controller.start();

        return controller;
    }

    public static void createAndStartControllerManager() throws Exception {
        ControllerManager controllerManager = createControllerManager();
        start(controllerManager);
        String tip = "The ControllerManager boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();
        log.info(tip);
        System.out.printf("%s%n", tip);
    }

    public static ControllerManager createControllerManager() throws Exception {
        NettyServerConfig controllerNettyServerConfig = (NettyServerConfig) nettyServerConfig.clone();
        ControllerManager controllerManager = new ControllerManager(controllerConfig, controllerNettyServerConfig, nettyClientConfig);
        // remember all configs to prevent discard
        controllerManager.getConfiguration().registerConfig(properties);
        return controllerManager;
    }

    public static ControllerManager start(final ControllerManager controllerManager) throws Exception {

        if (null == controllerManager) {
            throw new IllegalArgumentException("ControllerManager is null");
        }

        boolean initResult = controllerManager.initialize();
        if (!initResult) {
            controllerManager.shutdown();
            System.exit(-3);
        }

        Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(log, (Callable<Void>) () -> {
            controllerManager.shutdown();
            return null;
        }));

        controllerManager.start();

        return controllerManager;
    }

    public static void shutdown(final NamesrvController controller) {
        controller.shutdown();
    }

    public static void shutdown(final ControllerManager controllerManager) {
        controllerManager.shutdown();
    }

    public static Options buildCommandlineOptions(final Options options) {
        Option opt = new Option("c", "configFile", true, "Name server config properties file");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p", "printConfigItem", false, "Print all config items");
        opt.setRequired(false);
        options.addOption(opt);
        return options;
    }

    public static Properties getProperties() {
        return properties;
    }
}
