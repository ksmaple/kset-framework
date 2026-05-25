package com.kset.cloud.nacos;

import com.kset.cloud.config.KsetCloudProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NacosConfigConventionTest {

    @Test
    void resolvesDataIds() {
        KsetCloudProperties properties = new KsetCloudProperties();
        NacosConfigConvention convention = new NacosConfigConvention(properties);
        assertEquals("order-service-route-rules", convention.dubboRouteDataId("order-service"));
        assertEquals("gw-gateway-routes", convention.gatewayRouteDataId("gw"));
    }
}
