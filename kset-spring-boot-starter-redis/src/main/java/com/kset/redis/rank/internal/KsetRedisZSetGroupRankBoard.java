package com.kset.redis.rank.internal;

import com.kset.redis.rank.KsetRedisRankEntry;
import com.kset.redis.rank.KsetRedisRankOrder;
import com.kset.redis.rank.group.KsetRedisGroupRankBoard;
import com.kset.redis.rank.group.KsetRedisGroupRankChangeEvent;
import com.kset.redis.rank.group.KsetRedisGroupRankChangeListener;
import com.kset.redis.rank.group.KsetRedisGroupRankChangeType;
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
 * 分组 + 组内成员双 ZSET 实现。
 */
public final class KsetRedisZSetGroupRankBoard implements KsetRedisGroupRankBoard {

    private final String boardId;
    private final KsetRedisRankKeys keys;
    private final RedisTemplate<String, Object> template;
    private final KsetRedisRankOrder order;
    private final Duration boardTtl;
    private final List<KsetRedisGroupRankChangeListener> listeners = new CopyOnWriteArrayList<>();

    public KsetRedisZSetGroupRankBoard(String boardId,
                                       KsetRedisRankKeys keys,
                                       RedisTemplate<String, Object> template,
                                       KsetRedisRankOrder order,
                                       Duration boardTtl) {
        this.boardId = Objects.requireNonNull(boardId, "boardId");
        this.keys = Objects.requireNonNull(keys, "keys");
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
    public List<KsetRedisRankEntry> topGroups(int count) {
        return readRange(keys.groupTotalKey(), 1, count);
    }

    @Override
    public List<KsetRedisRankEntry> groupRange(int startRank, int count) {
        return readRange(keys.groupTotalKey(), startRank, count);
    }

    @Override
    public Optional<Integer> groupRankOf(String groupId) {
        return rankAt(keys.groupTotalKey(), groupId);
    }

    @Override
    public Optional<Double> groupScoreOf(String groupId) {
        return scoreAt(keys.groupTotalKey(), groupId);
    }

    @Override
    public List<KsetRedisRankEntry> topMembers(String groupId, int count) {
        return readRange(keys.groupMembersKey(groupId), 1, count);
    }

    @Override
    public Optional<Integer> memberRankInGroup(String groupId, String memberId) {
        return rankAt(keys.groupMembersKey(groupId), memberId);
    }

    @Override
    public Optional<Double> memberScoreInGroup(String groupId, String memberId) {
        return scoreAt(keys.groupMembersKey(groupId), memberId);
    }

    @Override
    public double contribute(String groupId, String memberId, double delta) {
        return contribute(groupId, memberId, delta, null);
    }

    @Override
    public double contribute(String groupId, String memberId, double delta, KsetRedisGroupRankChangeListener listener) {
        String membersKey = keys.groupMembersKey(groupId);
        String groupsKey = keys.groupTotalKey();

        Integer prevMemberRank = rankAt(membersKey, memberId).orElse(null);
        Double prevMemberScore = scoreAt(membersKey, memberId).orElse(null);
        Integer prevGroupRank = rankAt(groupsKey, groupId).orElse(null);
        Double prevGroupScore = scoreAt(groupsKey, groupId).orElse(null);

        Double newMemberScore = zOps().incrementScore(membersKey, memberId, delta);
        zOps().incrementScore(groupsKey, groupId, delta);
        refreshTtl(membersKey, groupsKey);

        double memberScore = newMemberScore != null ? newMemberScore : 0D;
        int newMemberRank = rankAt(membersKey, memberId).orElseThrow();
        int newGroupRank = rankAt(groupsKey, groupId).orElseThrow();
        double newGroupScore = scoreAt(groupsKey, groupId).orElse(0D);

        publishMemberChange(groupId, memberId, prevMemberScore, memberScore, prevMemberRank, newMemberRank, listener);
        publishGroupChange(groupId, memberId, prevGroupScore, newGroupScore, prevGroupRank, newGroupRank, listener);
        return memberScore;
    }

    @Override
    public boolean removeMember(String groupId, String memberId) {
        String membersKey = keys.groupMembersKey(groupId);
        Double score = scoreAt(membersKey, memberId).orElse(null);
        Long removed = zOps().remove(membersKey, memberId);
        if (score != null && removed != null && removed > 0) {
            zOps().incrementScore(keys.groupTotalKey(), groupId, -score);
            refreshTtl(membersKey, keys.groupTotalKey());
            return true;
        }
        return removed != null && removed > 0;
    }

    @Override
    public boolean removeGroup(String groupId) {
        template.delete(keys.groupMembersKey(groupId));
        Long removed = zOps().remove(keys.groupTotalKey(), groupId);
        return removed != null && removed > 0;
    }

    @Override
    public KsetRedisGroupRankBoard addListener(KsetRedisGroupRankChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    @Override
    public void touchTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        template.expire(keys.groupTotalKey(), ttl);
    }

    private ZSetOperations<String, Object> zOps() {
        return template.opsForZSet();
    }

    private List<KsetRedisRankEntry> readRange(String key, int startRank, int count) {
        if (count <= 0 || startRank < 1) {
            return List.of();
        }
        long start = startRank - 1L;
        long end = start + count - 1L;
        Set<ZSetOperations.TypedTuple<Object>> tuples = order == KsetRedisRankOrder.HIGH_SCORE_FIRST
                ? zOps().reverseRangeWithScores(key, start, end)
                : zOps().rangeWithScores(key, start, end);
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

    private Optional<Integer> rankAt(String key, String member) {
        Long index = order == KsetRedisRankOrder.HIGH_SCORE_FIRST
                ? zOps().reverseRank(key, member)
                : zOps().rank(key, member);
        if (index == null) {
            return Optional.empty();
        }
        return Optional.of(index.intValue() + 1);
    }

    private Optional<Double> scoreAt(String key, String member) {
        return Optional.ofNullable(zOps().score(key, member));
    }

    private void refreshTtl(String... redisKeys) {
        if (boardTtl == null || boardTtl.isZero() || boardTtl.isNegative()) {
            return;
        }
        for (String key : redisKeys) {
            template.expire(key, boardTtl);
        }
    }

    private void publishMemberChange(String groupId,
                                     String memberId,
                                     Double previousScore,
                                     double newScore,
                                     Integer previousRank,
                                     int newRank,
                                     KsetRedisGroupRankChangeListener oneShot) {
        boolean rankChanged = previousRank == null || previousRank != newRank;
        KsetRedisGroupRankChangeEvent event = new KsetRedisGroupRankChangeEvent(
                boardId, KsetRedisGroupRankChangeType.MEMBER_IN_GROUP, groupId, memberId,
                previousScore, newScore, previousRank, newRank, rankChanged);
        dispatch(event, oneShot);
    }

    private void publishGroupChange(String groupId,
                                    String memberId,
                                    Double previousScore,
                                    double newScore,
                                    Integer previousRank,
                                    int newRank,
                                    KsetRedisGroupRankChangeListener oneShot) {
        boolean rankChanged = previousRank == null || previousRank != newRank;
        KsetRedisGroupRankChangeEvent event = new KsetRedisGroupRankChangeEvent(
                boardId, KsetRedisGroupRankChangeType.GROUP, groupId, memberId,
                previousScore, newScore, previousRank, newRank, rankChanged);
        dispatch(event, oneShot);
    }

    private void dispatch(KsetRedisGroupRankChangeEvent event, KsetRedisGroupRankChangeListener oneShot) {
        if (oneShot != null) {
            safeNotify(oneShot, event);
        }
        for (KsetRedisGroupRankChangeListener listener : listeners) {
            safeNotify(listener, event);
        }
    }

    private static void safeNotify(KsetRedisGroupRankChangeListener listener, KsetRedisGroupRankChangeEvent event) {
        try {
            listener.onRankChanged(event);
        } catch (RuntimeException ignored) {
            // 监听器异常不影响主流程
        }
    }
}
