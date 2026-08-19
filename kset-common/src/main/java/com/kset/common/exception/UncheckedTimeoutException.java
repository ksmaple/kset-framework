package com.kset.common.exception;

import java.util.concurrent.TimeoutException;

/**
 * Unchecked wrapper for {@link TimeoutException} so callers can catch timeout without a checked exception.
 */
public class UncheckedTimeoutException extends RuntimeException {

    public UncheckedTimeoutException(String message) {
        super(message);
    }

    public UncheckedTimeoutException(String message, TimeoutException cause) {
        super(message, cause);
    }

    public UncheckedTimeoutException(TimeoutException cause) {
        super(cause != null ? cause.getMessage() : "等待超时", cause);
    }
}
