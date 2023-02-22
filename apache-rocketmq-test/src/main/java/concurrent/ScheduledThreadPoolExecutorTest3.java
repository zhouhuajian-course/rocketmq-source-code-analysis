package concurrent;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ScheduledThreadPoolExecutorTest3 {
    public static void main(String[] args) {
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(2);
        Runnable runnable =  () -> {
            log.info("task started");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.info("task ended");
        };
        Runnable runnable2 =  () -> {
            log.info("task2 started");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.info("task2 ended");
        };
        /**
         * 创建并执行一个周期性操作，该操作在给定的初始延迟之后首先启用，
         * 然后在一次执行终止和下一次执行开始之间的给定延迟之后启用。
         * 如果任务的任何执行遇到异常，则禁止后续执行。否则，任务将仅通过取消或终止执行者而终止。
         */
        log.info("scheduleAtFixedRate called");
        scheduledThreadPoolExecutor.scheduleWithFixedDelay(runnable, 1, 10, TimeUnit.SECONDS);
        // scheduledThreadPoolExecutor.scheduleWithFixedDelay(runnable2, 1, 10, TimeUnit.SECONDS);
        /**
         * // 48秒 调用scheduleAtFixedRate
         * 11:05:48.247 [main] INFO concurrent.ScheduledThreadPoolExecutorTest3 - scheduleAtFixedRate called
         * // 49秒 初始延迟1秒 第一次执行任务
         * 11:05:49.267 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest3 - task started
         * // 54秒 任务耗时5秒
         * 11:05:54.274 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest3 - task ended
         * // 04秒 第一个任务结束后，再延迟10秒，执行第二次任务 （这里说明新的任务是需要再上一次任务执行完后再延迟10秒再执行 对比scheduleAtFixedRate）
         * 11:06:04.280 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest3 - task started
         * 11:06:09.281 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest3 - task ended
         * 11:06:19.286 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest3 - task started
         * 11:06:24.300 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest3 - task ended
         */
        // log.info("main thread ended");
    }
}
