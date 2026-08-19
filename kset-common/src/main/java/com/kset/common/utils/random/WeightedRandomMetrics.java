package com.kset.common.utils.random;

import com.kset.common.utils.JsonUtil;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 加权随机运行时指标快照。
 */
@Data
@Builder
public class WeightedRandomMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private WeightedRandomAlgorithm algorithm;
    private long totalDraws;
    private Long seed;
    private int configVersion;
    private long snapshotTimeMs;
    private double maxDeviation;
    private double chiSquare;
    @Builder.Default
    private List<ItemMetric> items = new ArrayList<>();

    @Data
    @Builder
    public static class ItemMetric implements Serializable {
        private static final long serialVersionUID = 1L;

        private String key;
        private long count;
        private double weight;
        private double expectedRate;
        private double actualRate;
        private double deviation;
    }

    public String toJson() {
        try {
            return JsonUtil.toJson(this);
        } catch (RuntimeException e) {
            return "{\"error\":\"failed to serialize metrics\"}";
        }
    }

    @Override
    public String toString() {
        return toJson();
    }
}
