package com.kset.cloud.nacos;

import com.kset.cloud.config.KsetCloudProperties;
import org.springframework.core.env.Environment;

/**
 * Nacos 配置命名约定。
 *
 * <p>{@link #namespace()} / {@link #group()} 优先读取 Spring Cloud 标准
 * {@code spring.cloud.nacos.*}，未配置时回退 {@code kset.cloud.nacos.*} 默认值。</p>
 */
public class NacosConfigConvention {

    public static final String COMMON_CONFIG_DATA_ID = "kset-common.yaml";

    private final KsetCloudProperties properties;
    private final Environment environment;

    public NacosConfigConvention(KsetCloudProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public String group() {
        String group = environment.getProperty("spring.cloud.nacos.config.group");
        if (group != null && !group.isBlank()) {
            return group;
        }
        group = environment.getProperty("spring.cloud.nacos.discovery.group");
        if (group != null && !group.isBlank()) {
            return group;
        }
        return properties.getNacos().getGroup();
    }

    public String namespace() {
        String namespace = environment.getProperty("spring.cloud.nacos.config.namespace");
        if (namespace != null && !namespace.isBlank()) {
            return namespace;
        }
        namespace = environment.getProperty("spring.cloud.nacos.discovery.namespace");
        if (namespace != null && !namespace.isBlank()) {
            return namespace;
        }
        return properties.getNacos().getNamespace();
    }

    public String appConfigDataId(String appName) {
        return appName + ".yaml";
    }

    public String commonConfigDataId() {
        return properties.getNacos().getCommonConfigDataId();
    }

    public String flowRuleDataId(String appName) {
        return appName + "-flow-rules";
    }

    public String degradeRuleDataId(String appName) {
        return appName + "-degrade-rules";
    }

    public String paramFlowRuleDataId(String appName) {
        return appName + "-param-flow-rules";
    }

    public String dubboRouteDataId(String appName) {
        return appName + "-route-rules";
    }

    public String gatewayRouteDataId(String appName) {
        return appName + "-gateway-routes";
    }

    public String gatewayFlowRuleDataId(String appName) {
        return appName + "-gateway-flow-rules";
    }
}
