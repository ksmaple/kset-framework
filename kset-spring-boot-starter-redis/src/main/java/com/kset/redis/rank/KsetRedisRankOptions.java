package com.kset.redis.rank;

import com.kset.redis.key.KsetRedisKeys;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * 单榜/分组榜的代码侧配置（不依赖 YAML）。
 */
public final class KsetRedisRankOptions {

    private final String boardId;
    private final String redisKey;
    private final KsetRedisRankOrder order;
    private final Duration ttl;

    private KsetRedisRankOptions(String boardId, String redisKey, KsetRedisRankOrder order, Duration ttl) {
        this.boardId = Objects.requireNonNull(boardId, "boardId");
        this.redisKey = redisKey;
        this.order = order != null ? order : KsetRedisRankOrder.HIGH_SCORE_FIRST;
        this.ttl = ttl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String boardId) {
        return new Builder().boardId(boardId);
    }

    public static KsetRedisRankOptions of(String boardId) {
        return builder(boardId).build();
    }

    public String boardId() {
        return boardId;
    }

    public String redisKey() {
        return redisKey;
    }

    public KsetRedisRankOrder order() {
        return order;
    }

    public Duration ttl() {
        return ttl;
    }

    /**
     * 解析榜根 Redis key：显式 {@link #redisKey()} 优先，否则 {@code keyPrefix + boardId}。
     */
    public String resolveRedisKey(String keyPrefix) {
        if (StringUtils.hasText(redisKey)) {
            return KsetRedisKeys.normalizeKey(redisKey);
        }
        return KsetRedisKeys.joinPrefix(keyPrefix, boardId);
    }

    public static final class Builder {

        private String boardId;
        private String redisKey;
        private KsetRedisRankOrder order;
        private Duration ttl;

        public Builder boardId(String boardId) {
            this.boardId = boardId;
            return this;
        }

        public Builder redisKey(String redisKey) {
            this.redisKey = redisKey;
            return this;
        }

        public Builder order(KsetRedisRankOrder order) {
            this.order = order;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public KsetRedisRankOptions build() {
            if (!StringUtils.hasText(boardId)) {
                throw new IllegalArgumentException("boardId must not be blank");
            }
            return new KsetRedisRankOptions(boardId, redisKey, order, ttl);
        }
    }
}
