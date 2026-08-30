package com.kset.redis.core;

import com.kset.redis.config.KsetRedisProperties;

/**
 * 高危 Redis 操作的流式/分批参数（SCAN、UNLINK、分块 MGET 等）。
 */
public final class KsetRedisStreamSettings {

    private final int scanBatchSize;
    private final int deleteBatchSize;
    private final int mgetChunkSize;
    private final int hashScanCount;
    private final boolean useUnlink;

    public KsetRedisStreamSettings(KsetRedisProperties properties) {
        KsetRedisProperties.Stream stream = properties.getStream();
        this.scanBatchSize = stream.getScanBatchSize();
        this.deleteBatchSize = stream.getDeleteBatchSize();
        this.mgetChunkSize = stream.getMgetChunkSize();
        this.hashScanCount = stream.getHashScanCount();
        this.useUnlink = stream.isUseUnlink();
    }

    public int scanBatchSize() {
        return scanBatchSize;
    }

    public int deleteBatchSize() {
        return deleteBatchSize;
    }

    public int mgetChunkSize() {
        return mgetChunkSize;
    }

    public int hashScanCount() {
        return hashScanCount;
    }

    public boolean useUnlink() {
        return useUnlink;
    }
}
