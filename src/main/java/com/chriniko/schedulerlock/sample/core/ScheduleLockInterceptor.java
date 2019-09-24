package com.chriniko.schedulerlock.sample.core;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.javatuples.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;


@Slf4j
public class ScheduleLockInterceptor implements MethodInterceptor {

    @Inject
    private TransactionTemplate transactionTemplate;

    @Inject
    private JdbcTemplate jdbcTemplate;

    private final Clock utcClock;

    private final String jvmName;

    public ScheduleLockInterceptor() {
        this.jvmName = ManagementFactory.getRuntimeMXBean().getName();
        this.utcClock = Clock.systemUTC();

        Runtime.getRuntime().addShutdownHook(new Thread(this::releaseHeldLocks));
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        ScheduleLock scheduleLockInfo = methodInvocation.getMethod().getDeclaredAnnotation(ScheduleLock.class);
        String resourceName = scheduleLockInfo.resourceName();
        long ttlInSeconds = scheduleLockInfo.ttlInSeconds();

        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            log.debug("[jvm process: {}] will try to obtain lock for resourceName: {}", jvmName, resourceName);
            Pair<Boolean, String /*ownerId*/> shouldProceed = tryLock(resourceName, ttlInSeconds);

            if (shouldProceed.getValue0()) {
                try {
                    log.debug("[jvm process: {}] lock acquired for resourceName: {}", jvmName, resourceName);
                    return methodInvocation.proceed(); // Note: execute scheduler's logic.
                } finally {
                    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                        @Override
                        protected void doInTransactionWithoutResult(TransactionStatus status) {
                            removeLock(jvmName, resourceName);
                        }
                    });
                    log.debug("[jvm process: {}] lock released for resourceName: {}", jvmName, resourceName);
                }
            }

            log.debug("[jvm process: {}] could not obtain lock, lock is held from another jvm process with id: {}, skipping method invocation...",
                    jvmName, shouldProceed.getValue1());
            return null;
        } catch (Exception error) {
            Throwable unwrappedError = Exceptions.unwrap(error);
            log.error("error occurred during schedule lock operation", unwrappedError);
            return null;
        }
    }

    @PreDestroy
    public void clear() {
        this.releaseHeldLocks();
    }

    private Pair<Boolean, String> tryLock(String resourceName, long ttlInSeconds) {
        return transactionTemplate.execute(txStatus -> {
            Pair<String, Timestamp> ownedBy = findLock(resourceName);

            if (ownedBy == null) {
                Instant now = Instant.now(utcClock);

                Timestamp lockedAt = Timestamp.from(now);
                Timestamp lockUntil = Timestamp.from(now.plusSeconds(ttlInSeconds));

                insertLock(jvmName, resourceName, lockedAt, lockUntil);
                return Pair.with(true, null);
            } else {
                Instant lockHeldUntil = ownedBy.getValue1().toInstant();
                Instant now = Instant.now(utcClock);

                // Note: check if ttl has expired.
                if (now.isAfter(lockHeldUntil)) {
                    String ownedById = ownedBy.getValue0();

                    log.debug("[jvm process: {}] ttl has expired, will grab lock from jvm process with id: {}",
                            jvmName, ownedById);

                    Timestamp lockedAt = Timestamp.from(now);
                    Timestamp lockUntil = Timestamp.from(now.plusSeconds(ttlInSeconds));

                    // Note: grab lock.
                    removeLock(ownedById, resourceName);
                    insertLock(jvmName, resourceName, lockedAt, lockUntil);

                    return Pair.with(true, null);
                }
                return Pair.with(false, ownedBy.getValue0());
            }
        });
    }

    private Pair<String, Timestamp> findLock(String resourceName) {
        return jdbcTemplate.query(
                "select owner_id, lock_until from schedulers_lock where resource_name = ?",
                resultSet -> {
                    if (resultSet.first()) {
                        return Pair.with(
                                resultSet.getString("owner_id"),
                                resultSet.getTimestamp("lock_until")
                        );
                    }
                    return null;
                },
                resourceName
        );
    }

    private void insertLock(String ownerId, String resourceName, Timestamp lockedAt, Timestamp lockUntil) {
        jdbcTemplate.update(
                "insert into schedulers_lock(owner_id, resource_name, locked_at, lock_until) VALUES (?, ?, ?, ?)",
                ownerId, resourceName, lockedAt, lockUntil
        );
    }

    private void removeLock(String ownerId, String resourceName) {
        jdbcTemplate.update("delete from schedulers_lock where owner_id = ? and resource_name = ?",
                ownerId, resourceName);
    }

    private void releaseHeldLocks() {
        log.debug("[jvm process: {}] will clear any held locks...", jvmName);

        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                jdbcTemplate.update("delete from schedulers_lock where owner_id = ?", jvmName);
            }
        });
    }
}
