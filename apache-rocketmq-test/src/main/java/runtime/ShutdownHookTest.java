package runtime;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShutdownHookTest {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                log.info("do something before JVM shuts down and exits");
            }
        });

        // 测试主动kill进程
        // try {
        //     Thread.sleep(5000);
        // } catch (InterruptedException e) {
        //     throw new RuntimeException(e);
        // }
        log.info("main thread ends, and the JVM is going to shut down.");

        /**
         * 15:34:54.286 [main] INFO runtime.ShutdownHookTest - main thread ends, and the JVM is going to shut down.
         * 15:34:55.307 [Thread-0] INFO runtime.ShutdownHookTest - do something before JVM shuts down and exits
         */

        /**
         * // 主动kill掉进程
         * 15:39:48.342 [Thread-0] INFO runtime.ShutdownHookTest - do something before JVM shuts down and exits
         */
    }
}
