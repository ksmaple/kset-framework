package com.kset.nacos.registrar;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.kset.cloud.config.KsetCloudProperties;
import com.kset.cloud.nacos.NacosConfigConvention;
import com.kset.cloud.spi.CloudRuleProvider;
import com.kset.cloud.spi.CloudRuleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KsetCloudRuleRegistrarTest {

    @Test
    void loadsInitialRulesBeforeRegisteringListener() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ConfigService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(configService);
        CloudRuleProvider ruleProvider = mock(CloudRuleProvider.class);
        when(ruleProvider.ruleType()).thenReturn(CloudRuleType.DUBBO_ROUTE);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "orders");
        KsetCloudProperties properties = new KsetCloudProperties();
        NacosConfigConvention convention = new NacosConfigConvention(properties, environment);
        String dataId = convention.dubboRouteDataId("orders");
        when(configService.getConfig(dataId, convention.group(), 5000)).thenReturn("initial-rules");

        new KsetCloudRuleRegistrarAutoConfiguration.KsetCloudRuleRegistrar(
                provider, List.of(ruleProvider), properties, convention, environment);

        var order = inOrder(configService, ruleProvider);
        order.verify(configService).getConfig(dataId, convention.group(), 5000);
        order.verify(ruleProvider).onRuleChanged("initial-rules");
        order.verify(configService).addListener(eq(dataId), eq(convention.group()), any(Listener.class));
    }

    @Test
    void usesConfiguredGatewayDataId() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ConfigService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(configService);
        CloudRuleProvider ruleProvider = mock(CloudRuleProvider.class);
        when(ruleProvider.ruleType()).thenReturn(CloudRuleType.GATEWAY_ROUTE);
        KsetCloudProperties properties = new KsetCloudProperties();
        properties.getGateway().setRouteDataId("custom-gateway-routes");
        MockEnvironment environment = new MockEnvironment();
        NacosConfigConvention convention = new NacosConfigConvention(properties, environment);

        new KsetCloudRuleRegistrarAutoConfiguration.KsetCloudRuleRegistrar(
                provider, List.of(ruleProvider), properties, convention, environment);

        verify(configService).getConfig("custom-gateway-routes", convention.group(), 5000);
        verify(configService).addListener(
                eq("custom-gateway-routes"), eq(convention.group()), any(Listener.class));
    }
}
