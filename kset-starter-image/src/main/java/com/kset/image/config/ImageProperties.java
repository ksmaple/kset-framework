package com.kset.image.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * kset.image.* 图片 AI 能力配置：base-url/api-key 为理解与生成共用，vision 与 generation 各自独立开关与模型。
 */
@ConfigurationProperties(prefix = "kset.image")
public class ImageProperties {

    /** 总开关，关闭后理解与生成均不装配。 */
    private boolean enabled = true;

    /** OpenAI 兼容服务地址，默认 OpenAI。 */
    private String baseUrl = "https://api.openai.com/v1";

    /** 服务 API Key；为空时不装配客户端并在启动时告警。 */
    private String apiKey;

    private final Vision vision = new Vision();

    private final Generation generation = new Generation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Vision getVision() { return vision; }
    public Generation getGeneration() { return generation; }

    /** 图片理解（多模态对话）配置。 */
    public static class Vision {
        /** 图片理解开关。 */
        private boolean enabled = true;
        /** 理解模型名。 */
        private String model = "gpt-4.1-mini";
        /** 连接与读取各自的超时，须在 1ms 至 Integer.MAX_VALUE ms 之间。 */
        private Duration timeout = Duration.ofSeconds(60);
        /** 默认最大输出 token 数。 */
        private int maxTokens = 2048;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    /** 图片生成配置。 */
    public static class Generation {
        /** 图片生成开关。 */
        private boolean enabled = true;
        /** 生成模型名。 */
        private String model = "gpt-image-2.5-flare";
        /** 默认尺寸，OpenAI 规格如 1024x1024；其他服务按其协议配置。 */
        private String defaultSize = "1024x1024";
        /** 连接与读取各自的超时，须在 1ms 至 Integer.MAX_VALUE ms 之间。 */
        private Duration timeout = Duration.ofSeconds(120);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getDefaultSize() { return defaultSize; }
        public void setDefaultSize(String defaultSize) { this.defaultSize = defaultSize; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }
}
