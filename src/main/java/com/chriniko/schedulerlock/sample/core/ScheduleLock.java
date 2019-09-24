package com.chriniko.schedulerlock.sample.core;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScheduleLock {

    String resourceName();

    // Note: ttl == time to live.
    long ttlInSeconds();

}
