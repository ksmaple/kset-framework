package com.kset.agent.core.dto;

/**
 * LLM 供应健康快照（跨模块只读视图）。
 *
 * @param degraded          是否处于降级态
 * @param windowSize        滑动窗口样本数
 * @param recentFailures    窗口内失败次数（不含 429）
 * @param recentRateLimits  窗口内 429 次数
 * @param lastBadOutcomeAt  最近一次坏样本时间戳（毫秒），无则为 null
 */
public record LlmHealthSnapshotDTO(boolean degraded, int windowSize, long recentFailures,
                                   long recentRateLimits, Long lastBadOutcomeAt) {
}
