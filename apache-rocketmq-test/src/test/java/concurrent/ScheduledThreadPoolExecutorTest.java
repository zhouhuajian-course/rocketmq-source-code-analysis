package concurrent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ScheduledThreadPoolExecutorTest {
    private ScheduledThreadPoolExecutor executor;
    private Runnable task;

    @BeforeEach
    public void before() {
        executor = initExecutor();
        task = initTask();
    }

    private ScheduledThreadPoolExecutor initExecutor() {
        return new ScheduledThreadPoolExecutor(2);
    }

    private Runnable initTask() {
        long start = System.currentTimeMillis();
        return () -> {
            print("start task: " + getPeriod(start, System.currentTimeMillis()));
            sleep(SECONDS, 10);
            print("end task: " + getPeriod(start, System.currentTimeMillis()));
        };
    }

    @Test
    public void testFixedTask() {
        print("start main thread");
        executor.scheduleAtFixedRate(task, 15, 30, SECONDS);
        sleep(SECONDS, 120);
        print("end main thread");
    }

    @Test
    public void testDelayedTask() {
        print("start main thread");
        executor.scheduleWithFixedDelay(task, 15, 30, SECONDS);
        sleep(SECONDS, 120);
        print("end main thread");
    }

    private void sleep(TimeUnit unit, long time) {
        try {
            unit.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private int getPeriod(long start, long end) {
        return (int)(end - start) / 1000;
    }

    private void print(String msg) {
        System.out.println(msg);
    }
}