package com.kset.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KSet 云服务统一配置
 */
@ConfigurationProperties(prefix = "kset.cloud")
public class KsetCloudProperties {

    private final Nacos nacos = new Nacos();
    private final Sentinel sentinel = new Sentinel();
    private final Dubbo dubbo = new Dubbo();
    private final Loadbalancer loadbalancer = new Loadbalancer();
    private final Gateway gateway = new Gateway();

    public Nacos getNacos() {
        return nacos;
    }

    public Sentinel getSentinel() {
        return sentinel;
    }

    public Dubbo getDubbo() {
        return dubbo;
    }

    public Loadbalancer getLoadbalancer() {
        return loadbalancer;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public static class Nacos {
        private String namespace = "dev";
        private String group = "KSET_GROUP";
        private String configPrefix = "kset";
        private String commonConfigDataId = "kset-common.yaml";

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getConfigPrefix() {
            return configPrefix;
        }

        public void setConfigPrefix(String configPrefix) {
            this.configPrefix = configPrefix;
        }

        public String getCommonConfigDataId() {
            return commonConfigDataId;
        }

        public void setCommonConfigDataId(String commonConfigDataId) {
            this.commonConfigDataId = commonConfigDataId;
        }
    }

    public static class Sentinel {
        private boolean enabled = true;
        private String flowRuleDataId;
        private String degradeRuleDataId;
        private String paramFlowRuleDataId;
        private String gatewayFlowRuleDataId;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFlowRuleDataId() {
            return flowRuleDataId;
        }

        public void setFlowRuleDataId(String flowRuleDataId) {
            this.flowRuleDataId = flowRuleDataId;
        }

        public String getDegradeRuleDataId() {
            return degradeRuleDataId;
        }

        public void setDegradeRuleDataId(String degradeRuleDataId) {
            this.degradeRuleDataId = degradeRuleDataId;
        }

        public String getParamFlowRuleDataId() {
            return paramFlowRuleDataId;
        }

        public void setParamFlowRuleDataId(String paramFlowRuleDataId) {
            this.paramFlowRuleDataId = paramFlowRuleDataId;
        }

        public String getGatewayFlowRuleDataId() {
            return gatewayFlowRuleDataId;
        }

        public void setGatewayFlowRuleDataId(String gatewayFlowRuleDataId) {
            this.gatewayFlowRuleDataId = gatewayFlowRuleDataId;
        }
    }

    public static class Dubbo {
        private boolean tracePropagationEnabled = true;
        private boolean grayEnabled = true;
        private String grayMetadataKey = "version";
        private String defaultGrayTag = "stable";

        public boolean isTracePropagationEnabled() {
            return tracePropagationEnabled;
        }

        public void setTracePropagationEnabled(boolean tracePropagationEnabled) {
            this.tracePropagationEnabled = tracePropagationEnabled;
        }

        public boolean isGrayEnabled() {
            return grayEnabled;
        }

        public void setGrayEnabled(boolean grayEnabled) {
            this.grayEnabled = grayEnabled;
        }

        public String getGrayMetadataKey() {
            return grayMetadataKey;
        }

        public void setGrayMetadataKey(String grayMetadataKey) {
            this.grayMetadataKey = grayMetadataKey;
        }

        public String getDefaultGrayTag() {
            return defaultGrayTag;
        }

        public void setDefaultGrayTag(String defaultGrayTag) {
            this.defaultGrayTag = defaultGrayTag;
        }
    }

    public static class Loadbalancer {
        private String grayHeader = "X-Gray-Tag";
        private String metadataKey = "version";

        public String getGrayHeader() {
            return grayHeader;
        }

        public void setGrayHeader(String grayHeader) {
            this.grayHeader = grayHeader;
        }

        public String getMetadataKey() {
            return metadataKey;
        }

        public void setMetadataKey(String metadataKey) {
            this.metadataKey = metadataKey;
        }
    }

    public static class Gateway {
        private boolean enabled = true;
        private String routeDataId;
        private boolean sentinelEnabled = true;
        private boolean authEnabled = false;
        private boolean corsEnabled = true;
        private String authTokenHeader = "X-Auth-Token";
        private String traceHeader = "X-Trace-Id";
        private String grayHeader = "X-Gray-Tag";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRouteDataId() {
            return routeDataId;
        }

        public void setRouteDataId(String routeDataId) {
            this.routeDataId = routeDataId;
        }

        public boolean isSentinelEnabled() {
            return sentinelEnabled;
        }

        public void setSentinelEnabled(boolean sentinelEnabled) {
            this.sentinelEnabled = sentinelEnabled;
        }

        public boolean isAuthEnabled() {
            return authEnabled;
        }

        public void setAuthEnabled(boolean authEnabled) {
            this.authEnabled = authEnabled;
        }

        public boolean isCorsEnabled() {
            return corsEnabled;
        }

        public void setCorsEnabled(boolean corsEnabled) {
            this.corsEnabled = corsEnabled;
        }

        public String getAuthTokenHeader() {
            return authTokenHeader;
        }

        public void setAuthTokenHeader(String authTokenHeader) {
            this.authTokenHeader = authTokenHeader;
        }

        public String getTraceHeader() {
            return traceHeader;
        }

        public void setTraceHeader(String traceHeader) {
            this.traceHeader = traceHeader;
        }

        public String getGrayHeader() {
            return grayHeader;
        }

        public void setGrayHeader(String grayHeader) {
            this.grayHeader = grayHeader;
        }
    }
}
