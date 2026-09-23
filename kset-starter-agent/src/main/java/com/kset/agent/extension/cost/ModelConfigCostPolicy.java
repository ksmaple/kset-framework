package com.kset.agent.extension.cost;

import com.kset.agent.spi.ModelPricing;
import com.kset.agent.spi.ModelPricingPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Optional;

@Component
public class ModelConfigCostPolicy implements AiModelCostPolicy {
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private final ModelPricingPort modelConfigService;

    public ModelConfigCostPolicy(ModelPricingPort modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @Override
    public Optional<BigDecimal> estimate(String modelName, long inputTokens, long outputTokens) {
        return findModelConfig(modelName).flatMap(config -> calculate(config, inputTokens, outputTokens));
    }

    private Optional<ModelPricing> findModelConfig(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        Comparator<ModelPricing> priority = Comparator
                .comparing((ModelPricing config) -> Boolean.TRUE.equals(config.getActive())).reversed()
                .thenComparing(ModelPricing::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
        return modelConfigService.listAll().stream()
                .filter(config -> modelName.equalsIgnoreCase(config.getModelName()))
                .sorted(priority)
                .findFirst();
    }

    private Optional<BigDecimal> calculate(ModelPricing config, long inputTokens, long outputTokens) {
        BigDecimal inputPrice = config.getInputPricePerMillionTokens();
        BigDecimal outputPrice = config.getOutputPricePerMillionTokens();
        if (inputPrice == null || outputPrice == null) {
            return Optional.empty();
        }
        BigDecimal inputCost = inputPrice.multiply(BigDecimal.valueOf(inputTokens));
        BigDecimal outputCost = outputPrice.multiply(BigDecimal.valueOf(outputTokens));
        return Optional.of(inputCost.add(outputCost).divide(ONE_MILLION, 8, RoundingMode.HALF_UP));
    }
}
