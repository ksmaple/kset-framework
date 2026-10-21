package com.kset.dubbo.exception;

import org.apache.dubbo.rpc.RpcException;

import java.util.Objects;

/** Consumer-side classification based on Dubbo's stable exception codes. */
public enum DubboFailureType {
    NO_PROVIDER,
    UNAVAILABLE,
    TIMEOUT,
    UNKNOWN;

    public static DubboFailureType from(RpcException exception) {
        Objects.requireNonNull(exception, "exception");
        return switch (exception.getCode()) {
            case RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER -> NO_PROVIDER;
            case RpcException.NETWORK_EXCEPTION, RpcException.REGISTRY_EXCEPTION -> UNAVAILABLE;
            case RpcException.TIMEOUT_EXCEPTION, RpcException.TIMEOUT_TERMINATE -> TIMEOUT;
            default -> UNKNOWN;
        };
    }
}
