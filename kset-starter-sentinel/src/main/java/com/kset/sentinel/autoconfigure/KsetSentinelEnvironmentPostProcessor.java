package com.kset.sentinel.autoconfigure;

import com.kset.cloud.config.KsetCloudProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 补全 Sentinel 规则 dataId 默认值（仅由 starter-sentinel 注册）。
 */
public class KsetSentinelEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ksetSentinelDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        KsetCloudProperties properties = new KsetCloudProperties();
        String appName = environment.getProperty("spring.application.name", "application");

        if (properties.getSentinel().getFlowRuleDataId() == null) {
            properties.getSentinel().setFlowRuleDataId(appName + "-flow-rules");
        }
        if (properties.getSentinel().getDegradeRuleDataId() == null) {
            properties.getSentinel().setDegradeRuleDataId(appName + "-degrade-rules");
        }
        if (properties.getSentinel().getParamFlowRuleDataId() == null) {
            properties.getSentinel().setParamFlowRuleDataId(appName + "-param-flow-rules");
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfMissing(environment, defaults, "kset.cloud.sentinel.flow-rule-data-id",
                properties.getSentinel().getFlowRuleDataId());
        putIfMissing(environment, defaults, "kset.cloud.sentinel.degrade-rule-data-id",
                properties.getSentinel().getDegradeRuleDataId());
        putIfMissing(environment, defaults, "kset.cloud.sentinel.param-flow-rule-data-id",
                properties.getSentinel().getParamFlowRuleDataId());

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    private void putIfMissing(ConfigurableEnvironment environment, Map<String, Object> defaults,
                              String key, Object value) {
        if (!environment.containsProperty(key) && value != null) {
            defaults.put(key, value);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
