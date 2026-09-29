package com.kset.agent.core.port;

import com.kset.agent.core.dto.ListAiSessionQualityCommand;
import com.kset.agent.core.dto.AiSessionQualityDTO;
import com.kset.agent.core.dto.AiSessionQualityStatsDTO;

import java.time.LocalDate;
import java.util.List;

public interface AiSessionQualityQueryPort {
    List<AiSessionQualityDTO> list(ListAiSessionQualityCommand command);

    List<AiSessionQualityStatsDTO> dimensionStats(LocalDate startDate, LocalDate endDate);

    List<AiSessionQualityStatsDTO> dailyStats(LocalDate startDate, LocalDate endDate, String dimension);
}
