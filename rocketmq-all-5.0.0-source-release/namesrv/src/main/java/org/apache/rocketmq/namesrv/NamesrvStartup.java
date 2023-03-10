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
    // ??????????????????
    private static InternalLogger log;
    // ??????
    private static Properties properties = null;
    // namesrv??????
    private static NamesrvConfig namesrvConfig = null;
    // Netty ???????????????
    private static NettyServerConfig nettyServerConfig = null;
    // Netty ???????????????
    private static NettyClientConfig nettyClientConfig = null;
    // ???????????????
    private static ControllerConfig controllerConfig = null;

    public static void main(String[] args) {
        main0(args);
        // ?????????????????? ?????????
        controllerManagerMain();
    }

    public static void main0(String[] args) {
        try {
            // ?????????????????????????????? args????????????????????? args=[]
            parseCommandlineAndConfigFile(args);
            // ???????????????namesrv?????????
            createAndStartNamesrvController();
        } catch (Throwable e) {
            // ??????????????????????????????????????????
            e.printStackTrace();
            // ???????????? ??????????????????-1
            System.exit(-1);
        }

    }

    public static void controllerManagerMain() {
        try {
            // namesrc?????? ????????????????????? ??? namesrc?????? ??????false
            if (namesrvConfig.isEnableControllerInNamesrv()) {
                createAndStartControllerManager();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static void parseCommandlineAndConfigFile(String[] args) throws Exception {
        // ??????????????????
        // ???????????????
        // public static final String REMOTING_VERSION_KEY = "rocketmq.remoting.version";
        // Integer.toString(MQVersion.CURRENT_VERSION) "413"
        System.setProperty(RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
        //PackageConflictDetect.detectFastjson();
        // ??????????????? ?????????????????????  Options ?????? cli??????????????? ??????
        Options options = ServerUtil.buildCommandlineOptions(new Options());
        // ??????????????? ???????????????  ????????? mqnamesrv ?????? args ????????????????????? options ??????????????? PosixParser
        CommandLine commandLine = ServerUtil.parseCmdLine("mqnamesrv",
                                                          args,
                                                          buildCommandlineOptions(options),
                                                          new PosixParser());
        // ?????? ??? ?????? ???????????????
        if (null == commandLine) {
            // ?????? ?????? ?????????-1
            System.exit(-1);
            return;
        }

        // namesrv??????
        namesrvConfig = new NamesrvConfig();
        // netty ???????????????
        nettyServerConfig = new NettyServerConfig();
        // netty ???????????????
        nettyClientConfig = new NettyClientConfig();
        // netty ??????????????? ?????????????????? 9876
        nettyServerConfig.setListenPort(9876);
        // ???????????????
        controllerConfig = new ControllerConfig();
        // ???????????????????????? c
        if (commandLine.hasOption('c')) {
            // ???????????????????????? c
            // ??????????????????
            String file = commandLine.getOptionValue('c');
            // ?????????????????????null
            if (file != null) {
                // ??????????????????????????????
                // java.nio.file.Files Paths ?????????IO
                InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(file)));
                // ????????????
                properties = new Properties();
                // ?????? ?????? ???????????????
                properties.load(in);
                // ?????? ??? ?????? ?????? namesrv??????
                // ????????? ????????????????????? ????????????
                MixAll.properties2Object(properties, namesrvConfig);
                MixAll.properties2Object(properties, nettyServerConfig);
                MixAll.properties2Object(properties, nettyClientConfig);
                MixAll.properties2Object(properties, controllerConfig);
                // namesrv?????? ???????????????????????? file
                namesrvConfig.setConfigStorePath(file);
                // ?????? ???????????? ??????????????? ?????????????????????????????? ????????? ??????
                System.out.printf("load config properties file OK, %s%n", file);
                // ????????? ??????
                in.close();
            }
        }

        // ??????????????? ????????? p
        if (commandLine.hasOption('p')) {
            // ???????????? ?????????????????? ??????????????? null
            MixAll.printObjectProperties(null, namesrvConfig);
            MixAll.printObjectProperties(null, nettyServerConfig);
            MixAll.printObjectProperties(null, nettyClientConfig);
            MixAll.printObjectProperties(null, controllerConfig);
            System.exit(0);
        }

        MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), namesrvConfig);
        // ?????? ??? ?????? namesrv?????? ??????rocketmq???
        if (null == namesrvConfig.getRocketmqHome()) {
            // ?????? ROCKETMQ_HOME ????????????
            // ?????? RocketMQ ????????????
            System.out.printf("Please set the %s variable in your environment to match the location of the RocketMQ installation%n", MixAll.ROCKETMQ_HOME_ENV);
            System.exit(-2);
        }
        // slf4j ?????????????????? for Java ??????????????? ??????ILoggerFactory
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // ch\qos\logback\classic\joran\JoranConfigurator.class
        // logback ?????? joran joran?????????
        // Joran, logback's configuration system
        JoranConfigurator configurator = new JoranConfigurator();
        // ????????? ??????????????? ???????????????
        configurator.setContext(lc);
        // ??????????????? ??????
        lc.reset();
        // ????????? ????????? ????????? namesrv?????? ??????rocketmq??? + /conf/logback_namesrv.xml
        configurator.doConfigure(namesrvConfig.getRocketmqHome() + "/conf/logback_namesrv.xml");
        // ?????????????????? ??????????????? ????????????
        log = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);
        // ?????????????????? namesrc??????
        MixAll.printObjectProperties(log, namesrvConfig);
        // ?????????????????? netty???????????????
        MixAll.printObjectProperties(log, nettyServerConfig);
    }

    public static void createAndStartNamesrvController() throws Exception {
        // ??????namesrc?????????
        NamesrvController controller = createNamesrvController();
        // ???????????????
        start(controller);
        // ?????? name server ?????? ?????? ??????????????? ?????? ???????????? ??????????????????????????? ??????????????????
        // JSON
        String tip = "The Name Server boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();
        // ?????? ?????? ??????
        log.info(tip);
        // ?????? ???????????? ??????????????? ?????? ??????
        System.out.printf("%s%n", tip);
    }

    public static NamesrvController createNamesrvController() {
        // namesrc????????? namesrc?????? netty??????????????? netty???????????????
        final NamesrvController controller = new NamesrvController(namesrvConfig, nettyServerConfig, nettyClientConfig);
        // remember all configs to prevent discard
        // ????????? ???????????? ???????????? namesrc.properties ??????
        controller.getConfiguration().registerConfig(properties);
        // ???????????????
        return controller;
    }

    public static NamesrvController start(final NamesrvController controller) throws Exception {
        // ?????? ??? ?????? ???????????????
        if (null == controller) {
            // ?????? ??????????????????
            throw new IllegalArgumentException("NamesrvController is null");
        }
        // ????????? ?????????
        boolean initResult = controller.initialize();
        // ???????????????
        if (!initResult) {
            // ????????? ??????
            controller.shutdown();
            // ???????????? ????????? -3
            System.exit(-3);
        }
        // ?????? ???????????? ??????????????????
        Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(log, (Callable<Void>) () -> {
            // ????????? ??????
            controller.shutdown();
            return null;
        }));
        // ???????????????
        controller.start();

        return controller;
    }

    public static void createAndStartControllerManager() throws Exception {
        // ????????????????????????
        ControllerManager controllerManager = createControllerManager();
        // ????????????????????????
        start(controllerManager);
        // ?????????????????? ????????????
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
