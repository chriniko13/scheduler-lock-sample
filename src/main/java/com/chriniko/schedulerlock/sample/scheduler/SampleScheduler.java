package com.chriniko.schedulerlock.sample.scheduler;

import com.chriniko.schedulerlock.sample.core.Pools;
import com.chriniko.schedulerlock.sample.core.ScheduleLock;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SampleScheduler {

    private ScheduledExecutorService scheduledExecutor;

    @Inject
    void init() {
        log.info("init called!");

        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("SOME_EVENTS_SCHEDULER");
            return t;
        });

        scheduledExecutor.scheduleWithFixedDelay(
                this::consumeSomeEvents, 7,
                5, TimeUnit.SECONDS
        );

        Runtime.getRuntime().addShutdownHook(new Thread(this::clear));
    }

    private void clear() {
        Pools.clear(scheduledExecutor, "campaign events scheduler");
    }

    @ScheduleLock(resourceName = "some-events", ttlInSeconds = 60 * 2)
    void consumeSomeEvents() { // Note: not used private access modifier in order to picked up from aspect(interceptor).

        log.info("will consume events now...");

        // Some dummy business logic....
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            if (!Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException(e);
        }

        log.info("consumed events, will release db lock soon...");

    }

}
