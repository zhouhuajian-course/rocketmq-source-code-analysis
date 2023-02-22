package concurrent;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ScheduledThreadPoolExecutorTest2 {
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
         * 创建并执行一个周期性动作，
         * 该动作在给定的初始延迟后首先启用，
         * 然后在给定的周期内启用；也就是说，
         * 执行将在initialDelay之后开始，
         * 然后是initialDelay+period，
         * 然后是inalDelay+2*period等等。
         * 如果任务的任何执行遇到异常，则禁止后续执行。
         * 否则，任务将仅通过取消或终止执行者而终止。
         * 如果此任务的任何执行时间超过其周期，则后续执行可能会延迟开始，
         * 但不会同时执行
         */
        log.info("scheduleAtFixedRate called");
        scheduledThreadPoolExecutor.scheduleAtFixedRate(runnable, 1, 10, TimeUnit.SECONDS);
        scheduledThreadPoolExecutor.scheduleAtFixedRate(runnable2, 1, 10, TimeUnit.SECONDS);
        /**
         * // 26秒 调用scheduleAtFixedRate
         * 10:50:26.648 [main] INFO concurrent.ScheduledThreadPoolExecutorTest - scheduleAtFixedRate called
         * // 初始延迟1秒 第一次调用
         * // 27秒 池 1 线程 1 调用任务
         * 10:50:27.668 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest - task started
         * // 32秒 任务耗时5秒 结束
         * 10:50:32.676 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest - task ended
         * // 37秒 任务再次执行 （说明任务耗时没超过period情况下，启动新任务的时间是固定频率，新任务是严格的每10秒启动一个，不是上个任务完成后再过10秒启动）
         * 10:50:37.661 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest - task started
         * 10:50:42.673 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest - task ended
         * // 47秒 任务再次执行
         * 10:50:47.668 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest - task started
         * 10:50:52.670 [pool-1-thread-1] INFO concurrent.ScheduledThreadPoolExecutorTest - task ended
         */
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("main thread ended");
    }
}
