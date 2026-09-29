package com.kset.agent.core.spi;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 模型计价信息（成本估算策略用）。
 */
@Data
public class ModelPricing {
    private String modelName;
    private Boolean active;
    private Date updatedAt;
    private BigDecimal inputPricePerMillionTokens;
    private BigDecimal outputPricePerMillionTokens;
}
