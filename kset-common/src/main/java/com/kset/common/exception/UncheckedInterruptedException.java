package com.kset.common.exception;

/**
 * Unchecked wrapper for {@link InterruptedException}. The interrupt status is restored on the current thread.
 */
public class UncheckedInterruptedException extends RuntimeException {

    public UncheckedInterruptedException(String message, InterruptedException cause) {
        super(message, cause);
        Thread.currentThread().interrupt();
    }

    public UncheckedInterruptedException(InterruptedException cause) {
        this(cause != null ? cause.getMessage() : "线程已中断", cause);
    }
}
