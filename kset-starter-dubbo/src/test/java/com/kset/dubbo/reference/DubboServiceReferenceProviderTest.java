package com.kset.dubbo.reference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DubboServiceReferenceProviderTest {

    @Test
    void requiresRegistryAddress() {
        assertThat(new DubboServiceReferenceProvider(new MockEnvironment()).isAvailable()).isFalse();
        assertThat(new DubboServiceReferenceProvider(
                new MockEnvironment().withProperty("dubbo.registry.address", "N/A")).isAvailable()).isFalse();
        assertThat(new DubboServiceReferenceProvider(
                new MockEnvironment().withProperty("dubbo.registry.address", "nacos://127.0.0.1:8848"))
                .isAvailable()).isTrue();
    }
}
