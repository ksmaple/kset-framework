package com.kset.agent.core.extension.cost;

import java.math.BigDecimal;
import java.util.Optional;

public interface AiModelCostPolicy {
    Optional<BigDecimal> estimate(String modelName, long inputTokens, long outputTokens);
}
