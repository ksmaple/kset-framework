package com.kset.dubbo.exception;

import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DubboFailureTypeTest {

    @Test
    void classifiesNoProviderSeparately() {
        assertThat(DubboFailureType.from(new RpcException(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER)))
                .isEqualTo(DubboFailureType.NO_PROVIDER);
    }

    @Test
    void classifiesNetworkRegistryAndTimeoutFailures() {
        assertThat(DubboFailureType.from(new RpcException(RpcException.NETWORK_EXCEPTION)))
                .isEqualTo(DubboFailureType.UNAVAILABLE);
        assertThat(DubboFailureType.from(new RpcException(RpcException.REGISTRY_EXCEPTION)))
                .isEqualTo(DubboFailureType.UNAVAILABLE);
        assertThat(DubboFailureType.from(new RpcException(RpcException.TIMEOUT_EXCEPTION)))
                .isEqualTo(DubboFailureType.TIMEOUT);
        assertThat(DubboFailureType.from(new RpcException(RpcException.TIMEOUT_TERMINATE)))
                .isEqualTo(DubboFailureType.TIMEOUT);
    }

    @Test
    void doesNotPresentBusinessOrUnknownFailuresAsMaintenance() {
        assertThat(DubboFailureType.from(new RpcException(RpcException.BIZ_EXCEPTION)))
                .isEqualTo(DubboFailureType.UNKNOWN);
        assertThat(DubboFailureType.from(new RpcException("unclassified")))
                .isEqualTo(DubboFailureType.UNKNOWN);
    }
}
