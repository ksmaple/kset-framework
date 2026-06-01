package com.kset.cache.config;

import com.kset.cache.core.KsetCacheLayer;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "kset.cache")
/**
 * KSet 缓存组件配置，控制缓存层级、空值缓存和 single flight 行为。
 */
public class KsetCacheProperties {

    /**
     * 是否启用缓存组件。
     */
    private boolean enabled = true;
    /**
     * 未在注解中指定 layers 时使用的默认缓存层级。
     */
    private List<KsetCacheLayer> defaultLayers = new ArrayList<>(List.of(KsetCacheLayer.L1));
    /**
     * 默认是否缓存 null 结果，避免热点空值反复穿透到后端。
     */
    private boolean cacheNull = true;
    /**
     * null 结果默认 TTL。
     */
    private Duration nullTtl = Duration.ofMinutes(1);
    /**
     * 是否启用同 key 加载合并，降低缓存击穿时的并发回源。
     */
    private boolean singleFlightEnabled = true;
    private final L1 l1 = new L1();
    private final L2 l2 = new L2();
    private final Spring spring = new Spring();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<KsetCacheLayer> getDefaultLayers() {
        return defaultLayers;
    }

    public void setDefaultLayers(List<KsetCacheLayer> defaultLayers) {
        this.defaultLayers = defaultLayers != null ? new ArrayList<>(defaultLayers) : new ArrayList<>();
    }

    public boolean isCacheNull() {
        return cacheNull;
    }

    public void setCacheNull(boolean cacheNull) {
        this.cacheNull = cacheNull;
    }

    public Duration getNullTtl() {
        return nullTtl;
    }

    public void setNullTtl(Duration nullTtl) {
        this.nullTtl = nullTtl;
    }

    public boolean isSingleFlightEnabled() {
        return singleFlightEnabled;
    }

    public void setSingleFlightEnabled(boolean singleFlightEnabled) {
        this.singleFlightEnabled = singleFlightEnabled;
    }

    public L1 getL1() {
        return l1;
    }

    public L2 getL2() {
        return l2;
    }

    public Spring getSpring() {
        return spring;
    }

    public static class L1 {
        /**
         * 是否启用本地 L1 缓存。
         */
        private boolean enabled = true;
        /**
         * L1 默认 TTL。
         */
        private Duration defaultTtl = Duration.ofMinutes(5);
        /**
         * L1 本地缓存最大条目数。
         */
        private long maximumSize = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }

    public static class L2 {
        /**
         * 是否要求存在 L2 存储；为 true 时缺少 L2 会启动失败。
         */
        private boolean required = false;
        /**
         * L2 默认 TTL。
         */
        private Duration defaultTtl = Duration.ofMinutes(30);

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }
    }

    public static class Spring {
        /**
         * Whether to expose KSet cache as a Spring CacheManager.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
