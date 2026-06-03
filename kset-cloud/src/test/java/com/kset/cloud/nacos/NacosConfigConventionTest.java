package com.kset.cloud.nacos;

import com.kset.cloud.config.KsetCloudProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NacosConfigConventionTest {

    @Test
    void resolvesDataIds() {
        KsetCloudProperties properties = new KsetCloudProperties();
        MockEnvironment environment = new MockEnvironment();
        NacosConfigConvention convention = new NacosConfigConvention(properties, environment);
        assertEquals("order-service-route-rules", convention.dubboRouteDataId("order-service"));
        assertEquals("gw-gateway-routes", convention.gatewayRouteDataId("gw"));
    }

    @Test
    void prefersSpringCloudNacosProperties() {
        KsetCloudProperties properties = new KsetCloudProperties();
        properties.getNacos().setNamespace("fallback-ns");
        properties.getNacos().setGroup("FALLBACK_GROUP");

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.cloud.nacos.config.namespace", "prod-ns");
        environment.setProperty("spring.cloud.nacos.config.group", "PROD_GROUP");

        NacosConfigConvention convention = new NacosConfigConvention(properties, environment);
        assertEquals("prod-ns", convention.namespace());
        assertEquals("PROD_GROUP", convention.group());
    }
}
