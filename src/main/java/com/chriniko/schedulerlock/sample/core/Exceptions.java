package com.chriniko.schedulerlock.sample.core;

public final class Exceptions {

    private Exceptions() {
    }

    public static Throwable unwrap(Throwable input) {
        Throwable result = input;

        while (result.getCause() != null) {
            result = result.getCause();
        }

        return result;
    }
}
