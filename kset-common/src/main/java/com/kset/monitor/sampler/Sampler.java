package com.kset.monitor.sampler;

/**
 * 采样策略（trace / transaction 共用）。
 */
public interface Sampler {

    boolean shouldSample(String traceId, String type);
}
