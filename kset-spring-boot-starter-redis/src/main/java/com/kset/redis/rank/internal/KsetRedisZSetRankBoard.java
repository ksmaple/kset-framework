package com.kset.redis.rank.internal;

import com.kset.redis.rank.KsetRedisRankBoard;
import com.kset.redis.rank.KsetRedisRankChangeEvent;
import com.kset.redis.rank.KsetRedisRankChangeListener;
import com.kset.redis.rank.KsetRedisRankEntry;
import com.kset.redis.rank.KsetRedisRankOrder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于 ZSET 的单榜实现。
 */
public final class KsetRedisZSetRankBoard implements KsetRedisRankBoard {

    private final String boardId;
    private final String redisKey;
    private final RedisTemplate<String, Object> template;
    private final KsetRedisRankOrder order;
    private final Duration boardTtl;
    private final List<KsetRedisRankChangeListener> listeners = new CopyOnWriteArrayList<>();

    public KsetRedisZSetRankBoard(String boardId,
                                  String redisKey,
                                  RedisTemplate<String, Object> template,
                                  KsetRedisRankOrder order,
                                  Duration boardTtl) {
        this.boardId = Objects.requireNonNull(boardId, "boardId");
        this.redisKey = Objects.requireNonNull(redisKey, "redisKey");
        this.template = Objects.requireNonNull(template, "template");
        this.order = order != null ? order : KsetRedisRankOrder.HIGH_SCORE_FIRST;
        this.boardTtl = boardTtl;
    }

    @Override
    public String boardId() {
        return boardId;
    }

    @Override
    public KsetRedisRankOrder order() {
        return order;
    }

    @Override
    public List<KsetRedisRankEntry> top(int count) {
        return range(1, count);
    }

    @Override
    public List<KsetRedisRankEntry> range(int startRank, int count) {
        if (count <= 0 || startRank < 1) {
            return List.of();
        }
        long start = startRank - 1L;
        long end = start + count - 1L;
        Set<ZSetOperations.TypedTuple<Object>> tuples = order == KsetRedisRankOrder.HIGH_SCORE_FIRST
                ? zOps().reverseRangeWithScores(redisKey, start, end)
                : zOps().rangeWithScores(redisKey, start, end);
        return toEntries(tuples, (int) startRank);
    }

    @Override
    public Optional<Integer> rankOf(String member) {
        Long index = order == KsetRedisRankOrder.HIGH_SCORE_FIRST
                ? zOps().reverseRank(redisKey, member)
                : zOps().rank(redisKey, member);
        if (index == null) {
            return Optional.empty();
        }
        return Optional.of(index.intValue() + 1);
    }

    @Override
    public Optional<Double> scoreOf(String member) {
        return Optional.ofNullable(zOps().score(redisKey, member));
    }

    @Override
    public Optional<KsetRedisRankEntry> entryOf(String member) {
        return rankOf(member).flatMap(rank -> scoreOf(member).map(score -> new KsetRedisRankEntry(rank, member, score)));
    }

    @Override
    public double increment(String member, double delta) {
        return increment(member, delta, null);
    }

    @Override
    public double increment(String member, double delta, KsetRedisRankChangeListener listener) {
        Integer previousRank = rankOf(member).orElse(null);
        Double previousScore = scoreOf(member).orElse(null);
        Double newScore = zOps().incrementScore(redisKey, member, delta);
        refreshTtl();
        int newRank = rankOf(member).orElseThrow(() -> new IllegalStateException("member missing after increment"));
        double score = newScore != null ? newScore : 0D;
        publishChange(member, previousScore, score, previousRank, newRank, listener);
        return score;
    }

    @Override
    public boolean setScore(String member, double score) {
        return setScore(member, score, null);
    }

    @Override
    public boolean setScore(String member, double score, KsetRedisRankChangeListener listener) {
        Integer previousRank = rankOf(member).orElse(null);
        Double previousScore = scoreOf(member).orElse(null);
        boolean added = Boolean.TRUE.equals(zOps().add(redisKey, member, score));
        refreshTtl();
        int newRank = rankOf(member).orElseThrow(() -> new IllegalStateException("member missing after setScore"));
        publishChange(member, previousScore, score, previousRank, newRank, listener);
        return added;
    }

    @Override
    public boolean remove(String member) {
        Long removed = zOps().remove(redisKey, member);
        return removed != null && removed > 0;
    }

    @Override
    public long size() {
        Long size = zOps().size(redisKey);
        return size != null ? size : 0L;
    }

    @Override
    public void clear() {
        template.delete(redisKey);
    }

    @Override
    public KsetRedisRankBoard addListener(KsetRedisRankChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    @Override
    public void touchTtl(Duration ttl) {
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            template.expire(redisKey, ttl);
        }
    }

    private ZSetOperations<String, Object> zOps() {
        return template.opsForZSet();
    }

    private void refreshTtl() {
        if (boardTtl != null && !boardTtl.isZero() && !boardTtl.isNegative()) {
            template.expire(redisKey, boardTtl);
        }
    }

    private List<KsetRedisRankEntry> toEntries(Set<ZSetOperations.TypedTuple<Object>> tuples, int startRank) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<KsetRedisRankEntry> entries = new ArrayList<>(tuples.size());
        int rank = startRank;
        for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
            if (tuple.getValue() == null) {
                continue;
            }
            double score = tuple.getScore() != null ? tuple.getScore() : 0D;
            entries.add(new KsetRedisRankEntry(rank++, String.valueOf(tuple.getValue()), score));
        }
        return entries;
    }

    private void publishChange(String member,
                               Double previousScore,
                               double newScore,
                               Integer previousRank,
                               int newRank,
                               KsetRedisRankChangeListener oneShot) {
        boolean rankChanged = previousRank == null || previousRank != newRank;
        KsetRedisRankChangeEvent event = new KsetRedisRankChangeEvent(
                boardId, member, previousScore, newScore, previousRank, newRank, rankChanged);
        if (oneShot != null) {
            safeNotify(oneShot, event);
        }
        for (KsetRedisRankChangeListener listener : listeners) {
            safeNotify(listener, event);
        }
    }

    private static void safeNotify(KsetRedisRankChangeListener listener, KsetRedisRankChangeEvent event) {
        try {
            listener.onRankChanged(event);
        } catch (RuntimeException ignored) {
            // 监听器异常不影响主流程
        }
    }
}
