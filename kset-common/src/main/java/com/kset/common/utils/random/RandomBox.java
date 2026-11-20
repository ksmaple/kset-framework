package com.kset.common.utils.random;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 加权随机盒子：按概率权重抽取 key，支持临时隐藏部分项。
 *
 * <p>薄封装 {@link WeightedRandomEngine}，不累积指标。
 */
public final class RandomBox {

    private final WeightedRandomEngine engine;
    private final List<String> hiddenKeys = new ArrayList<>();

    private RandomBox(Map<String, Double> probabilities) {
        this.engine = WeightedRandomEngine.builder()
                .weights(probabilities)
                .algorithm(WeightedRandomAlgorithm.WHEEL)
                .metricsEnabled(false)
                .build();
    }

    public static RandomBox of(Map<String, Double> probabilities) {
        if (probabilities == null || probabilities.isEmpty()) {
            throw new IllegalArgumentException("概率配置不能为空");
        }
        return new RandomBox(probabilities);
    }

    public static RandomBox build(Map<String, Double> probabilities) {
        return of(probabilities);
    }

    public RandomBox hidden(String key) {
        hiddenKeys.add(key);
        return this;
    }

    public String random() {
        return engine.draw(hiddenKeys.toArray(String[]::new));
    }
}
