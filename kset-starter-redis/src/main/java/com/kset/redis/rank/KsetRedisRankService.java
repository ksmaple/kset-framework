package com.kset.redis.rank;

import com.kset.redis.key.KsetRedisKeyNamespace;
import com.kset.redis.key.KsetRedisKeys;
import com.kset.redis.rank.internal.KsetRedisRankKeys;
import com.kset.redis.rank.internal.KsetRedisZSetGroupRankBoard;
import com.kset.redis.rank.internal.KsetRedisZSetRankBoard;
import com.kset.redis.rank.group.KsetRedisGroupRankBoard;
import com.kset.redis.monitor.KsetRedisMonitor;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 排行榜工厂：榜单参数均在代码中通过 {@link KsetRedisRankOptions} 指定。
 */
public class KsetRedisRankService {

    public static final String DEFAULT_KEY_PREFIX = KsetRedisKeys.normalizeTrailingColon(
            KsetRedisKeys.join("kset", KsetRedisKeyNamespace.RANK));

    private final RedisTemplate<String, Object> template;
    private final String keyPrefix;
    private final boolean monitorEnabled;
    private final ConcurrentMap<String, KsetRedisRankBoard> boards = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, KsetRedisGroupRankBoard> groupBoards = new ConcurrentHashMap<>();

    public static Builder builder(RedisTemplate<String, Object> template) {
        return new Builder(template);
    }

    public KsetRedisRankService(RedisTemplate<String, Object> template) {
        this(template, DEFAULT_KEY_PREFIX);
    }

    public KsetRedisRankService(RedisTemplate<String, Object> template, String keyPrefix) {
        this(template, keyPrefix, false);
    }

    public KsetRedisRankService(RedisTemplate<String, Object> template, String keyPrefix, boolean monitorEnabled) {
        this.template = Objects.requireNonNull(template, "template");
        this.keyPrefix = KsetRedisKeys.normalizeTrailingColon(
                keyPrefix != null ? keyPrefix : DEFAULT_KEY_PREFIX);
        this.monitorEnabled = monitorEnabled;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public KsetRedisRankBoard board(String boardId) {
        return board(KsetRedisRankOptions.builder(boardId).build());
    }

    public KsetRedisRankBoard board(String boardId, KsetRedisRankOrder order) {
        return board(KsetRedisRankOptions.builder(boardId).order(order).build());
    }

    public KsetRedisRankBoard board(String boardId, KsetRedisRankOrder order, Duration ttl) {
        return board(KsetRedisRankOptions.builder(boardId).order(order).ttl(ttl).build());
    }

    public KsetRedisRankBoard board(KsetRedisRankOptions options) {
        Objects.requireNonNull(options, "options");
        String cacheKey = cacheKey(options);
        return boards.computeIfAbsent(cacheKey, k -> createBoard(options));
    }

    public KsetRedisGroupRankBoard groupBoard(String boardId) {
        return groupBoard(KsetRedisRankOptions.builder(boardId).build());
    }

    public KsetRedisGroupRankBoard groupBoard(String boardId, KsetRedisRankOrder order, Duration ttl) {
        return groupBoard(KsetRedisRankOptions.builder(boardId).order(order).ttl(ttl).build());
    }

    public KsetRedisGroupRankBoard groupBoard(KsetRedisRankOptions options) {
        Objects.requireNonNull(options, "options");
        String cacheKey = "g:" + cacheKey(options);
        return groupBoards.computeIfAbsent(cacheKey, k -> createGroupBoard(options));
    }

    private KsetRedisRankBoard createBoard(KsetRedisRankOptions options) {
        String redisKey = options.resolveRedisKey(keyPrefix);
        KsetRedisRankBoard board = new KsetRedisZSetRankBoard(
                options.boardId(),
                redisKey,
                template,
                options.order(),
                options.ttl());
        return monitorEnabled ? KsetRedisMonitor.wrapRankBoard(board) : board;
    }

    private KsetRedisGroupRankBoard createGroupBoard(KsetRedisRankOptions options) {
        KsetRedisRankKeys keys = KsetRedisRankKeys.forRoot(options.resolveRedisKey(keyPrefix));
        if (!keys.groupTotalKey().equals(keys.legacyGroupTotalKey())
                && Boolean.TRUE.equals(template.hasKey(keys.legacyGroupTotalKey()))) {
            throw new IllegalStateException("legacy group rank data requires migration: "
                    + keys.legacyGroupTotalKey());
        }
        KsetRedisGroupRankBoard board = new KsetRedisZSetGroupRankBoard(
                options.boardId(),
                keys,
                template,
                options.order(),
                options.ttl());
        return monitorEnabled ? KsetRedisMonitor.wrapGroupRankBoard(board) : board;
    }

    private static String cacheKey(KsetRedisRankOptions options) {
        return options.boardId()
                + "|" + options.order().name()
                + "|" + (options.ttl() != null ? options.ttl() : "none")
                + "|" + (options.redisKey() != null ? options.redisKey() : "auto");
    }

    public static final class Builder {

        private final RedisTemplate<String, Object> template;
        private String keyPrefix = DEFAULT_KEY_PREFIX;
        private boolean monitorEnabled;

        private Builder(RedisTemplate<String, Object> template) {
            this.template = Objects.requireNonNull(template, "template");
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public Builder monitorEnabled(boolean monitorEnabled) {
            this.monitorEnabled = monitorEnabled;
            return this;
        }

        public KsetRedisRankService build() {
            return new KsetRedisRankService(template, keyPrefix, monitorEnabled);
        }
    }
}
