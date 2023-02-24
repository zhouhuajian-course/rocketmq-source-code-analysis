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
package org.apache.rocketmq.common;

import java.io.IOException;
import java.util.Map;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;

// abstract class ConfigManager
// common
public abstract class ConfigManager {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.COMMON_LOGGER_NAME);

    public abstract String encode();

    public boolean load() {
        String fileName = null;
        try {
            // config file path
            fileName = this.configFilePath();
            // mixall file to string
            // json string
            String jsonString = MixAll.file2String(fileName);

            if (null == jsonString || jsonString.length() == 0) {
                // load bak
                return this.loadBak();
            } else {
                // decode json string
                this.decode(jsonString);
                // log info load filename ok
                log.info("load " + fileName + " OK");
                // return true
                return true;
            }
        } catch (Exception e) {
            log.error("load " + fileName + " failed, and try to load backup file", e);
            return this.loadBak();
        }
    }

    public abstract String configFilePath();

    private boolean loadBak() {
        String fileName = null;
        try {
            // config file path
            fileName = this.configFilePath();
            // filename + .bak
            String jsonString = MixAll.file2String(fileName + ".bak");
            if (jsonString != null && jsonString.length() > 0) {
                // decode json string
                this.decode(jsonString);
                // load filename ok
                log.info("load " + fileName + " OK");
                return true;
            }
        } catch (Exception e) {
            // log error load filename failed
            log.error("load " + fileName + " Failed", e);
            return false;
        }

        return true;
    }

    public abstract void decode(final String jsonString);

    public synchronized <T> void persist(String topicName, T t) {
        // stub for future
        this.persist();
    }

    public synchronized <T> void persist(Map<String, T> m) {
        // stub for future
        this.persist();
    }

    public synchronized void persist() {
        String jsonString = this.encode(true);
        if (jsonString != null) {
            String fileName = this.configFilePath();
            try {
                MixAll.string2File(jsonString, fileName);
            } catch (IOException e) {
                log.error("persist file " + fileName + " exception", e);
            }
        }
    }

    public abstract String encode(final boolean prettyFormat);
}
