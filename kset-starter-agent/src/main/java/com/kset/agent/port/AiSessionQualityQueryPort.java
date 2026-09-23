package com.kset.agent.port;

import com.kset.agent.dto.ListAiSessionQualityCommand;
import com.kset.agent.dto.AiSessionQualityDTO;
import com.kset.agent.dto.AiSessionQualityStatsDTO;

import java.time.LocalDate;
import java.util.List;

public interface AiSessionQualityQueryPort {
    List<AiSessionQualityDTO> list(ListAiSessionQualityCommand command);

    List<AiSessionQualityStatsDTO> dimensionStats(LocalDate startDate, LocalDate endDate);

    List<AiSessionQualityStatsDTO> dailyStats(LocalDate startDate, LocalDate endDate, String dimension);
}
