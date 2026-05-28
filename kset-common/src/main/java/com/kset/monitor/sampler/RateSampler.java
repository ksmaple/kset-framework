package com.kset.monitor.sampler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 按固定比率采样（1.0 表示全采样）。
 */
public final class RateSampler implements Sampler {

    private final double rate;

    public RateSampler(double rate) {
        this.rate = Math.clamp(rate, 0.0, 1.0);
    }

    @Override
    public boolean shouldSample(String traceId, String type) {
        if (rate >= 1.0) {
            return true;
        }
        if (rate <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }
}
