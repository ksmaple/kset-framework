package com.kset.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 会话质量统计 DTO
 */
@Data
public class AiSessionQualityStatsDTO {

    /** 维度 */
    private String dimension;

    /** 维度中文名 */
    private String dimensionLabel;

    /** 统计日期（按日统计时） */
    private LocalDate statDate;

    /** 调用次数 */
    private Long callCount;

    /** 总输入 Token */
    private Long totalInputTokens;

    /** 总输出 Token */
    private Long totalOutputTokens;

    /** 总 Token */
    private Long totalTokens;

    /** 平均耗时(ms) */
    private BigDecimal avgDurationMs;

    /** 成功率(%) */
    private BigDecimal successRate;

    /** 平均相关性评分 */
    private BigDecimal avgRelevanceScore;

    /** 平均用户满意度 */
    private BigDecimal avgUserSatisfaction;
}
