package com.kset.redis.rank.internal;

import com.kset.redis.rank.KsetRedisRankEntry;
import com.kset.redis.rank.KsetRedisRankOrder;
import com.kset.redis.rank.group.KsetRedisGroupRankBoard;
import com.kset.redis.rank.group.KsetRedisGroupRankChangeEvent;
import com.kset.redis.rank.group.KsetRedisGroupRankChangeListener;
import com.kset.redis.rank.group.KsetRedisGroupRankChangeType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
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

    private static final byte[] CONTRIBUTE_SCRIPT = """
            for i = 1, 2 do
              local kind = redis.call('TYPE', KEYS[i]).ok
              if kind ~= 'none' and kind ~= 'zset' then return redis.error_reply('rank key type mismatch') end
            end
            local totalTtl = redis.call('PTTL', KEYS[2])
            if totalTtl == -1 and tonumber(ARGV[4]) > 0 then
              return redis.error_reply('rank board TTL mismatch')
            end
            local previousGroupScore = redis.call('ZSCORE', KEYS[2], ARGV[2])
            local resetMember = totalTtl == -2 or not previousGroupScore
            local resetGroup = not resetMember and redis.call('EXISTS', KEYS[1]) == 0
            local delta = tonumber(ARGV[3])
            local member = resetMember and 0 or tonumber(redis.call('ZSCORE', KEYS[1], ARGV[1]) or '0')
            local group = resetGroup and 0 or tonumber(previousGroupScore or '0')
            if not delta or not member or not group or
               math.abs(member + delta) == math.huge or math.abs(group + delta) == math.huge or
               member + delta ~= member + delta or group + delta ~= group + delta then
              return redis.error_reply('invalid rank score')
            end
            if resetMember then redis.call('DEL', KEYS[1]) end
            if resetGroup then redis.call('ZREM', KEYS[2], ARGV[2]) end
            local score = redis.call('ZINCRBY', KEYS[1], ARGV[3], ARGV[1])
            redis.call('ZINCRBY', KEYS[2], ARGV[3], ARGV[2])
            if totalTtl == -2 then
              if tonumber(ARGV[4]) > 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[4])
                redis.call('PEXPIRE', KEYS[2], ARGV[4])
              end
            elseif totalTtl >= 0 then
              redis.call('PEXPIRE', KEYS[1], math.max(totalTtl, 1))
              redis.call('PEXPIRE', KEYS[2], math.max(totalTtl, 1))
            end
            return score
            """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] REMOVE_MEMBER_SCRIPT = """
            for i = 1, 2 do
              local kind = redis.call('TYPE', KEYS[i]).ok
              if kind ~= 'none' and kind ~= 'zset' then return redis.error_reply('rank key type mismatch') end
            end
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not score then return 0 end
            local group = redis.call('ZSCORE', KEYS[2], ARGV[2])
            if not group then
              return redis.error_reply('group score is missing')
            end
            local remaining = tonumber(group) - tonumber(score)
            if remaining ~= remaining or math.abs(remaining) == math.huge then
              return redis.error_reply('invalid rank score')
            end
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZINCRBY', KEYS[2], -tonumber(score), ARGV[2])
            return 1
            """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] REMOVE_GROUP_SCRIPT = """
            for i = 1, 2 do
              local kind = redis.call('TYPE', KEYS[i]).ok
              if kind ~= 'none' and kind ~= 'zset' then return redis.error_reply('rank key type mismatch') end
            end
            redis.call('DEL', KEYS[1])
            return redis.call('ZREM', KEYS[2], ARGV[1])
            """.getBytes(StandardCharsets.UTF_8);

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
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("delta must be finite");
        }
        String membersKey = keys.groupMembersKey(groupId);
        String groupsKey = keys.groupTotalKey();

        Integer prevMemberRank = rankAt(membersKey, memberId).orElse(null);
        Double prevMemberScore = scoreAt(membersKey, memberId).orElse(null);
        Integer prevGroupRank = rankAt(groupsKey, groupId).orElse(null);
        Double prevGroupScore = scoreAt(groupsKey, groupId).orElse(null);

        double memberScore = executeDouble(CONTRIBUTE_SCRIPT, membersKey, groupsKey,
                valueBytes(memberId), valueBytes(groupId), rawBytes(Double.toString(delta)),
                rawBytes(Long.toString(ttlMillis())));
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
        return executeLong(REMOVE_MEMBER_SCRIPT, membersKey, keys.groupTotalKey(),
                valueBytes(memberId), valueBytes(groupId)) > 0;
    }

    @Override
    public boolean removeGroup(String groupId) {
        return executeLong(REMOVE_GROUP_SCRIPT, keys.groupMembersKey(groupId),
                keys.groupTotalKey(), valueBytes(groupId)) > 0;
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
        throw new UnsupportedOperationException("group rank TTL is fixed at first contribution");
    }

    private ZSetOperations<String, Object> zOps() {
        return template.opsForZSet();
    }

    private double executeDouble(byte[] script, String firstKey, String secondKey, byte[]... arguments) {
        Object raw = execute(script, ReturnType.VALUE, firstKey, secondKey, arguments);
        if (!(raw instanceof byte[] bytes)) {
            throw new IllegalStateException("rank script returned no score");
        }
        return Double.parseDouble(new String(bytes, StandardCharsets.UTF_8));
    }

    private long executeLong(byte[] script, String firstKey, String secondKey, byte[]... arguments) {
        Object raw = execute(script, ReturnType.INTEGER, firstKey, secondKey, arguments);
        if (!(raw instanceof Number number)) {
            throw new IllegalStateException("rank script returned no count");
        }
        return number.longValue();
    }

    private Object execute(byte[] script, ReturnType returnType,
                           String firstKey, String secondKey, byte[]... arguments) {
        byte[][] keysAndArgs = new byte[arguments.length + 2][];
        keysAndArgs[0] = keyBytes(firstKey);
        keysAndArgs[1] = keyBytes(secondKey);
        System.arraycopy(arguments, 0, keysAndArgs, 2, arguments.length);
        return template.execute((RedisCallback<Object>) connection ->
                connection.scriptingCommands().eval(script, returnType, 2, keysAndArgs));
    }

    @SuppressWarnings("unchecked")
    private byte[] keyBytes(String key) {
        return ((RedisSerializer<String>) template.getKeySerializer()).serialize(key);
    }

    @SuppressWarnings("unchecked")
    private byte[] valueBytes(String value) {
        return ((RedisSerializer<Object>) template.getValueSerializer()).serialize(value);
    }

    private static byte[] rawBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private long ttlMillis() {
        return boardTtl != null && !boardTtl.isZero() && !boardTtl.isNegative()
                ? Math.max(1L, boardTtl.toMillis()) : 0L;
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
            
        }
    }
}
