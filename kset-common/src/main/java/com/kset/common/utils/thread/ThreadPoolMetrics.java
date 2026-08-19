package com.kset.common.utils.thread;

import com.kset.common.utils.JsonUtil;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;


@Data
@Builder
public class ThreadPoolMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    // === 配置 ===
    private String poolName;
    private int corePoolSize;
    private int maximumPoolSize;
    private long keepAliveTimeMs;
    private int queueCapacity;
    private boolean autoTuneEnabled;
    private long targetLatencyMs;
    private boolean priorityQueueEnabled;
    private int defaultPriority;

    // === 实时状态 ===
    private int poolSize;
    private int activeCount;
    private int largestPoolSize;
    private int queueSize;
    private int queueRemainingCapacity;

    // === 累计指标 ===
    private long submittedTasks;
    private long completedTasks;
    private long failedTasks;
    private long rejectedTasks;

    // === 派生指标 ===
    /** 成功率 [0.0, 1.0] */
    private double successRate;
    /** 平均执行耗时（毫秒） */
    private double avgExecutionTimeMs;
    /** 平均等待耗时（毫秒） */
    private double avgWaitTimeMs;
    /** 最大执行耗时（毫秒） */
    private long maxExecutionTimeMs;
    /** 最小执行耗时（毫秒） */
    private long minExecutionTimeMs;
    /** P99 执行耗时（毫秒） */
    private double p99ExecutionTimeMs;
    /** 吞吐量（任务/秒） */
    private double throughputPerSecond;

    // === 控制论反馈 ===
    /** 延迟误差比例 = (实际平均耗时 - 目标耗时) / 目标耗时 */
    private double latencyErrorRatio;
    private long lastTuneTimeMs;
    private String lastTuneAction;

    public String toJson() {
        try {
            return JsonUtil.toJson(this);
        } catch (RuntimeException e) {
            return "{\"error\":\"failed to serialize metrics\"}";
        }
    }

    @Override
    public String toString() {
        return toJson();
    }
}
