package com.kset.agent.dto;

import lombok.Data;

import java.util.Date;

/**
 * AI 会话质量记录 DTO
 */
@Data
public class AiSessionQualityDTO {

    private Long id;
    private String sessionId;
    private String messageId;
    private String dimension;
    private String dimensionLabel;
    private String modelName;
    private String provider;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Integer durationMs;
    private Boolean success;
    private Integer relevanceScore;
    private Integer hallucinationScore;
    private Integer toxicityScore;
    private Integer userSatisfaction;
    private Integer retryCount;
    private String errorType;
    private Date createdAt;
}
