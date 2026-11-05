package com.kset.gateway.autoconfigure;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.kset.cloud.config.KsetCloudProperties;
import com.kset.gateway.route.GatewayRouteRuleProvider;
import com.kset.cloud.nacos.NacosConfigConvention;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.concurrent.Executor;

@AutoConfiguration(afterName = "com.kset.nacos.registrar.KsetCloudRuleRegistrarAutoConfiguration")
@ConditionalOnClass(ConfigService.class)
public class KsetGatewayRouteListenerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ksetCloudRuleRegistrar")
    public GatewayRouteNacosListener gatewayRouteNacosListener(
            ObjectProvider<ConfigService> configServiceProvider,
            GatewayRouteRuleProvider routeRuleProvider,
            KsetCloudProperties properties,
            NacosConfigConvention convention,
            Environment environment) {
        return new GatewayRouteNacosListener(configServiceProvider, routeRuleProvider, properties, convention, environment);
    }

    @Slf4j
    static class GatewayRouteNacosListener {

        GatewayRouteNacosListener(ObjectProvider<ConfigService> configServiceProvider,
                                  GatewayRouteRuleProvider routeRuleProvider,
                                  KsetCloudProperties properties,
                                  NacosConfigConvention convention,
                                  Environment environment) {
            ConfigService configService = configServiceProvider.getIfAvailable();
            if (configService == null || !properties.getGateway().isEnabled()) {
                return;
            }

            String appName = environment.getProperty("spring.application.name", "application");
            String dataId = properties.getGateway().getRouteDataId();
            if (dataId == null || dataId.isBlank()) {
                dataId = convention.gatewayRouteDataId(appName);
            }

            try {
                String initial = configService.getConfig(dataId, convention.group(), 5000);
                if (initial != null) {
                    routeRuleProvider.onRuleChanged(initial);
                }
                configService.addListener(dataId, convention.group(), new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        routeRuleProvider.onRuleChanged(configInfo);
                    }
                });
                log.info("Gateway route Nacos listener registered: dataId={}", dataId);
            } catch (NacosException e) {
                log.warn("Failed to register gateway route listener: {}", e.getMessage());
            }
        }
    }
}
