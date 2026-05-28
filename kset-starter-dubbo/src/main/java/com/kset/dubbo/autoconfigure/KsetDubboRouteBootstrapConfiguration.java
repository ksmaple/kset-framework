package com.kset.dubbo.autoconfigure;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.kset.cloud.config.KsetCloudProperties;
import com.kset.dubbo.route.DubboRouteRuleHolder;
import com.kset.dubbo.route.DubboRouteRuleProvider;
import com.kset.cloud.nacos.NacosConfigConvention;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.concurrent.Executor;

@AutoConfiguration
@ConditionalOnClass(ConfigService.class)
@Slf4j
public class KsetDubboRouteBootstrapConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ksetCloudRuleRegistrar")
    public DubboRouteNacosBootstrap dubboRouteNacosBootstrap(
            ObjectProvider<ConfigService> configServiceProvider,
            DubboRouteRuleProvider routeRuleProvider,
            KsetCloudProperties properties,
            NacosConfigConvention convention,
            Environment environment) {
        DubboRouteRuleHolder.setMetadataKey(properties.getLoadbalancer().getMetadataKey());
        return new DubboRouteNacosBootstrap(configServiceProvider, routeRuleProvider, convention, environment);
    }

    static class DubboRouteNacosBootstrap {

        DubboRouteNacosBootstrap(ObjectProvider<ConfigService> configServiceProvider,
                                 DubboRouteRuleProvider routeRuleProvider,
                                 NacosConfigConvention convention,
                                 Environment environment) {
            ConfigService configService = configServiceProvider.getIfAvailable();
            if (configService == null) {
                return;
            }
            String appName = environment.getProperty("spring.application.name", "application");
            String dataId = convention.dubboRouteDataId(appName);
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
                log.info("Dubbo route Nacos listener registered: dataId={}", dataId);
            } catch (NacosException e) {
                log.warn("Failed to bootstrap Dubbo route from Nacos: {}", e.getMessage());
            }
        }
    }
}
