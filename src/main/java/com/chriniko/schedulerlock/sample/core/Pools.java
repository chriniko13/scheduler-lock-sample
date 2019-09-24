package com.chriniko.schedulerlock.sample.core;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class Pools {

    private Pools() {
    }

    public static ThreadPoolExecutor createPoolForIO(String workerName) {

        int processors = Runtime.getRuntime().availableProcessors();

        return new ThreadPoolExecutor(
                processors,
                2 * processors,
                1,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(1000),
                new ThreadFactory() {
                    private final AtomicInteger workerIdx = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName(String.format(workerName, workerIdx.getAndIncrement()));
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static void clear(ExecutorService executor, String message) {
        try {
            executor.shutdown();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("{} workers pool did not terminate in the specified time.", message);
                List<Runnable> droppedTasks = executor.shutdownNow();
                log.warn("{} workers pool was abruptly shut down. {} tasks will not be executed.", message, droppedTasks.size() );
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
