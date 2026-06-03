package com.kset.cloud.spi;

import com.kset.cloud.config.KsetCloudProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultGrayTagResolverTest {

    @Test
    void resolvesFromHeader() {
        KsetCloudProperties properties = new KsetCloudProperties();
        DefaultGrayTagResolver resolver = new DefaultGrayTagResolver(properties);
        assertEquals("v2", resolver.resolve("v2"));
    }

    @Test
    void fallsBackToDefaultTag() {
        KsetCloudProperties properties = new KsetCloudProperties();
        properties.getDubbo().setDefaultGrayTag("stable");
        DefaultGrayTagResolver resolver = new DefaultGrayTagResolver(properties);
        assertEquals("stable", resolver.resolve(null));
    }
}
