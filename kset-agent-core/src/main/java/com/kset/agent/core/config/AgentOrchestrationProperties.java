package com.kset.agent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Agent 编排的运行边界；安全与权限策略不允许通过该配置关闭。 */
@Component
@ConfigurationProperties(prefix = "ai.agent.orchestration")
public class AgentOrchestrationProperties {

    private int maxSteps = 20;
    private int maxPlanTasks = 3;
    private int maxParallelTools = 4;
    private int formatRetryLimit = 2;
    private int protocolErrorLimit = 2;
    private int modelRetryLimit = 3;
    private int contextCompressionRetryLimit = 2;
    private long retryBaseDelayMs = 500;
    private long retryMaxDelayMs = 10_000;
    private double retryJitterRatio = 0.2d;
    private Duration toolBatchTimeout = Duration.ofMinutes(2);
    /** Agent 在系统提示词中的身份称谓（宿主产品名），仅影响提示词身份描述，不影响协议。 */
    private String identity = "AI 助手";
    /** Agent/MCP 工具入口统一校验的功能权限点；留空表示不校验（由宿主自行管控）。 */
    private String toolEntryPermission = "";

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int value) { this.maxSteps = clamp(value, 1, 100); }
    public int getMaxPlanTasks() { return maxPlanTasks; }
    public void setMaxPlanTasks(int value) { this.maxPlanTasks = clamp(value, 1, 20); }
    public int getMaxParallelTools() { return maxParallelTools; }
    public void setMaxParallelTools(int value) { this.maxParallelTools = clamp(value, 1, 16); }
    public int getFormatRetryLimit() { return formatRetryLimit; }
    public void setFormatRetryLimit(int value) { this.formatRetryLimit = clamp(value, 0, 5); }
    public int getProtocolErrorLimit() { return protocolErrorLimit; }
    public void setProtocolErrorLimit(int value) { this.protocolErrorLimit = clamp(value, 0, 10); }
    public int getModelRetryLimit() { return modelRetryLimit; }
    public void setModelRetryLimit(int value) { this.modelRetryLimit = clamp(value, 0, 10); }
    public int getContextCompressionRetryLimit() { return contextCompressionRetryLimit; }
    public void setContextCompressionRetryLimit(int value) {
        this.contextCompressionRetryLimit = clamp(value, 0, 5);
    }
    public long getRetryBaseDelayMs() { return retryBaseDelayMs; }
    public void setRetryBaseDelayMs(long value) { this.retryBaseDelayMs = clamp(value, 0L, 60_000L); }
    public long getRetryMaxDelayMs() { return retryMaxDelayMs; }
    public void setRetryMaxDelayMs(long value) { this.retryMaxDelayMs = clamp(value, 0L, 300_000L); }
    public double getRetryJitterRatio() { return retryJitterRatio; }
    public void setRetryJitterRatio(double value) {
        this.retryJitterRatio = Double.isFinite(value) ? Math.max(0d, Math.min(value, 1d)) : 0d;
    }
    public Duration getToolBatchTimeout() { return toolBatchTimeout; }
    public void setToolBatchTimeout(Duration value) {
        if (value != null && !value.isNegative() && !value.isZero()) this.toolBatchTimeout = value;
    }

    public String getIdentity() { return identity; }
    public void setIdentity(String value) {
        if (value != null && !value.isBlank()) this.identity = value.trim();
    }
    public String getToolEntryPermission() { return toolEntryPermission; }
    public void setToolEntryPermission(String value) {
        this.toolEntryPermission = value == null ? "" : value.trim();
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }
    private long clamp(long value, long min, long max) { return Math.max(min, Math.min(value, max)); }
}
