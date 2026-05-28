package com.kset.sentinel.autoconfigure;

import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.cloud.config.KsetCloudProperties;
import com.kset.cloud.nacos.NacosConfigConvention;
import com.kset.cloud.spi.CloudRuleProvider;
import com.kset.cloud.spi.CloudRuleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass({FlowRuleManager.class, NacosDataSource.class})
@ConditionalOnProperty(prefix = "kset.cloud.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class KsetSentinelRuleAutoConfiguration {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Bean
    public SentinelRuleLoader sentinelRuleLoader(KsetCloudProperties properties,
                                                 NacosConfigConvention convention,
                                                 Environment environment,
                                                 ObjectProvider<List<CloudRuleProvider>> providers) {
        return new SentinelRuleLoader(properties, convention, environment, providers.getIfAvailable(List::of));
    }

    static class SentinelRuleLoader {

        SentinelRuleLoader(KsetCloudProperties properties,
                           NacosConfigConvention convention,
                           Environment environment,
                           List<CloudRuleProvider> providers) {
            String serverAddr = resolveServerAddr(environment);
            if (serverAddr == null || serverAddr.isBlank()) {
                log.warn("Nacos server address not configured, skip Sentinel rule loading from Nacos");
                return;
            }

            String appName = environment.getProperty("spring.application.name", "application");
            String group = convention.group();

            registerFlowRules(serverAddr, group, properties, providers, appName);
            registerDegradeRules(serverAddr, group, properties, providers, appName);
            registerParamFlowRules(serverAddr, group, properties, providers, appName);
            log.info("KSet Sentinel Nacos rule datasource initialized for app={}", appName);
        }

        private void registerFlowRules(String serverAddr, String group, KsetCloudProperties properties,
                                       List<CloudRuleProvider> providers, String appName) {
            String dataId = firstNonBlank(properties.getSentinel().getFlowRuleDataId(),
                    appName + "-flow-rules");
            ReadableDataSource<String, List<FlowRule>> source = new NacosDataSource<>(
                    serverAddr, group, dataId,
                    json -> {
                        try {
                            return OBJECT_MAPPER.readValue(json, new TypeReference<List<FlowRule>>() {});
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to parse flow rules", e);
                        }
                    });
            FlowRuleManager.register2Property(source.getProperty());
            notifyProviders(providers, CloudRuleType.SENTINEL_FLOW, dataId);
        }

        private void registerParamFlowRules(String serverAddr, String group, KsetCloudProperties properties,
                                            List<CloudRuleProvider> providers, String appName) {
            String dataId = firstNonBlank(properties.getSentinel().getParamFlowRuleDataId(),
                    appName + "-param-flow-rules");
            try {
                ReadableDataSource<String, List<ParamFlowRule>> source = new NacosDataSource<>(
                        serverAddr, group, dataId,
                        json -> {
                            try {
                                return OBJECT_MAPPER.readValue(json, new TypeReference<List<ParamFlowRule>>() {});
                            } catch (Exception e) {
                                throw new IllegalStateException("Failed to parse param flow rules", e);
                            }
                        });
                ParamFlowRuleManager.register2Property(source.getProperty());
                notifyProviders(providers, CloudRuleType.SENTINEL_PARAM_FLOW, dataId);
            } catch (Throwable t) {
                log.warn("Param flow rules not loaded (optional): {}", t.getMessage());
            }
        }

        private void registerDegradeRules(String serverAddr, String group, KsetCloudProperties properties,
                                          List<CloudRuleProvider> providers, String appName) {
            String dataId = firstNonBlank(properties.getSentinel().getDegradeRuleDataId(),
                    appName + "-degrade-rules");
            ReadableDataSource<String, List<DegradeRule>> source = new NacosDataSource<>(
                    serverAddr, group, dataId,
                    json -> {
                        try {
                            return OBJECT_MAPPER.readValue(json, new TypeReference<List<DegradeRule>>() {});
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to parse degrade rules", e);
                        }
                    });
            DegradeRuleManager.register2Property(source.getProperty());
            notifyProviders(providers, CloudRuleType.SENTINEL_DEGRADE, dataId);
        }

        private void notifyProviders(List<CloudRuleProvider> providers, CloudRuleType type, String dataId) {
            providers.stream()
                    .filter(p -> p.ruleType() == type)
                    .forEach(p -> p.onRuleChanged(dataId));
        }

        private String resolveServerAddr(Environment environment) {
            String addr = environment.getProperty("spring.cloud.nacos.config.server-addr");
            if (addr == null || addr.isBlank()) {
                addr = environment.getProperty("spring.cloud.nacos.discovery.server-addr");
            }
            return addr;
        }

        private String firstNonBlank(String primary, String fallback) {
            return primary != null && !primary.isBlank() ? primary : fallback;
        }
    }
}
