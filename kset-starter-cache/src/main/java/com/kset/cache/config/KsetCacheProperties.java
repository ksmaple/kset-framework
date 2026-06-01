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
    /**
     * 是否为最终 TTL 增加随机抖动，降低同批 key 同时过期概率。
     */
    private boolean ttlJitterEnabled = false;
    /**
     * TTL 抖动百分比，开启后在原 TTL 基础上随机增加 0 到该百分比。
     */
    private int ttlJitterPercent = 10;
    private final L1 l1 = new L1();
    private final L2 l2 = new L2();

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
        this.defaultLayers = defaultLayers != null && !defaultLayers.isEmpty()
                ? new ArrayList<>(defaultLayers)
                : new ArrayList<>(List.of(KsetCacheLayer.L1));
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

    public boolean isTtlJitterEnabled() {
        return ttlJitterEnabled;
    }

    public void setTtlJitterEnabled(boolean ttlJitterEnabled) {
        this.ttlJitterEnabled = ttlJitterEnabled;
    }

    public int getTtlJitterPercent() {
        return ttlJitterPercent;
    }

    public void setTtlJitterPercent(int ttlJitterPercent) {
        this.ttlJitterPercent = Math.max(0, ttlJitterPercent);
    }

    public L1 getL1() {
        return l1;
    }

    public L2 getL2() {
        return l2;
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
        /**
         * L1 本地缓存初始容量。
         */
        private int initialCapacity = 128;
        /**
         * 是否启用 Caffeine 统计。
         */
        private boolean recordStats = false;

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

        public int getInitialCapacity() {
            return initialCapacity;
        }

        public void setInitialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
        }

        public boolean isRecordStats() {
            return recordStats;
        }

        public void setRecordStats(boolean recordStats) {
            this.recordStats = recordStats;
        }
    }

    public static class L2 {
        /**
         * L2 默认 TTL。
         */
        private Duration defaultTtl = Duration.ofMinutes(30);

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }
    }

}
