package com.kset.common.tuya.config;

import lombok.Data;

/**
 * 涂鸦智能API配置
 * <p>
 * 支持两种方式指定接入地址：
 * <ul>
 *   <li>通过 region 枚举：TuyaConfig.builder().region(TuyaRegion.CN).build()</li>
 *   <li>通过自定义 baseUrl：TuyaConfig.builder().baseUrl("https://openapi.tuyacn.com").build()</li>
 * </ul>
 * region 优先级高于 baseUrl。
 *
 * @author kset
 */
@Data
public class TuyaConfig {

    /** 涂鸦开发者 client_id（即 Access ID） */
    private String clientId;

    /** 涂鸦开发者 secret（即 Access Secret） */
    private String secret;

    /** 数据中心区域（优先级高于baseUrl） */
    private TuyaRegion region;

    /** 自定义接入地址（当region未设置时使用） */
    private String baseUrl;

    private TuyaConfig() {
    }

    /**
     * 获取最终接入地址
     */
    public String getApiUrl() {
        if (region != null) {
            return region.getBaseUrl();
        }
        return baseUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String clientId;
        private String secret;
        private TuyaRegion region;
        private String baseUrl;

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        public Builder region(TuyaRegion region) {
            this.region = region;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public TuyaConfig build() {
            TuyaConfig config = new TuyaConfig();
            config.clientId = this.clientId;
            config.secret = this.secret;
            config.region = this.region;
            config.baseUrl = this.baseUrl;
            return config;
        }
    }
}
