package com.kset.agent.spi;

import java.util.List;

/**
 * 模型计价配置查询端口。
 */
public interface ModelPricingPort {

    /**
     * 列出全部模型计价配置；未接入计价体系时返回空列表（成本估算结果为空）。
     */
    List<ModelPricing> listAll();
}
