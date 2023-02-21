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
package org.apache.rocketmq.srvutil;

import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

// 服务端工具 服务端工具
public class ServerUtil {

    public static Options buildCommandlineOptions(final Options options) {
        // 选项 h 长选项 help 有参数 false 描述 输出帮助
        Option opt = new Option("h", "help", false, "Print help");
        // 选项 设置必须 false
        opt.setRequired(false);
        // options 添加选项 opt
        options.addOption(opt);
        // 选项 n 长选项 namesrcAddr 有参数 true 描述 name server 地址列表 例如
        opt =
            new Option("n", "namesrvAddr", true,
                "Name server address list, eg: '192.168.0.1:9876;192.168.0.2:9876'");
        opt.setRequired(false);
        options.addOption(opt);
        // 返回选项
        return options;
    }

    // 服务端工具 服务端工具 解析命令行
    public static CommandLine parseCmdLine(final String appName,
                                           String[] args,
                                           Options options,
                                           CommandLineParser parser) {
        // commons 通用 cli 命令行 HelpFormatter
        HelpFormatter hf = new HelpFormatter();
        hf.setWidth(110);
        CommandLine commandLine = null;
        try {
            // 解析器 解析 选项 参数
            commandLine = parser.parse(options, args);
            // 如果 命令行 有选项 h
            if (commandLine.hasOption('h')) {
                // 帮助格式器 输出帮助
                hf.printHelp(appName, options, true);
                // 系统退出 状态码0
                System.exit(0);
            }
        } catch (ParseException e) {
            // 出现解析异常，输出帮助
            hf.printHelp(appName, options, true);
            // 系统退出 状态码1
            System.exit(1);
        }
        // 返回命令行实例 commons cli CommandLine
        return commandLine;
    }

    public static void printCommandLineHelp(final String appName, final Options options) {
        HelpFormatter hf = new HelpFormatter();
        hf.setWidth(110);
        hf.printHelp(appName, options, true);
    }

    public static Properties commandLine2Properties(final CommandLine commandLine) {
        Properties properties = new Properties();
        Option[] opts = commandLine.getOptions();

        if (opts != null) {
            for (Option opt : opts) {
                String name = opt.getLongOpt();
                String value = commandLine.getOptionValue(name);
                if (value != null) {
                    properties.setProperty(name, value);
                }
            }
        }

        return properties;
    }

}
