package com.kset.agent.core.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 查询 AI 会话质量记录命令
 */
@Data
public class ListAiSessionQualityCommand {

    private LocalDate startDate;
    private LocalDate endDate;
    private String dimension;
    private String sessionId;
}
