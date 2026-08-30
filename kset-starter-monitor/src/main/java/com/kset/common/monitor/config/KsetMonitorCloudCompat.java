package com.kset.common.monitor.config;

import org.springframework.core.env.Environment;

/**
 * monitor 自持配置与 cloud 旧键的兼容解析：自持键优先，旧键回退，最后默认值。
 * 用于解除 monitor 对 kset-cloud 的依赖（1.0.15）。
 */
public final class KsetMonitorCloudCompat {

    public static final String DEFAULT_GRAY_TAG = "stable";
    public static final String DEFAULT_TRACE_HEADER = "X-Trace-Id";

    private KsetMonitorCloudCompat() {
    }

    public static boolean tracePropagationEnabled(KsetMonitorProperties properties, Environment environment) {
        Boolean own = properties.getDubbo().getTracePropagationEnabled();
        if (own != null) {
            return own;
        }
        return environment.getProperty("kset.cloud.dubbo.trace-propagation-enabled", Boolean.class, true);
    }

    public static String defaultGrayTag(KsetMonitorProperties properties, Environment environment) {
        String own = properties.getDubbo().getDefaultGrayTag();
        if (own != null && !own.isBlank()) {
            return own.trim();
        }
        String legacy = environment.getProperty("kset.cloud.dubbo.default-gray-tag");
        return legacy != null && !legacy.isBlank() ? legacy.trim() : DEFAULT_GRAY_TAG;
    }

    public static String gatewayTraceHeader(KsetMonitorProperties properties, Environment environment) {
        String own = properties.getGateway().getTraceHeader();
        if (own != null && !own.isBlank()) {
            return own.trim();
        }
        String legacy = environment.getProperty("kset.cloud.gateway.trace-header");
        return legacy != null && !legacy.isBlank() ? legacy.trim() : DEFAULT_TRACE_HEADER;
    }
}
