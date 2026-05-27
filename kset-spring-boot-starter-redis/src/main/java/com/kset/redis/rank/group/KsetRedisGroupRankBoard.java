package com.kset.redis.rank.group;

import com.kset.redis.rank.KsetRedisRankEntry;
import com.kset.redis.rank.KsetRedisRankOrder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 分组排行榜：一级为分组（战队/公会）总贡献，二级为组内成员贡献。
 * <p>
 * 写操作 {@link #contribute} 同时累加组内成员分与分组总分。
 */
public interface KsetRedisGroupRankBoard {

    String boardId();

    KsetRedisRankOrder order();

    // ── 一级：分组总榜 ──

    List<KsetRedisRankEntry> topGroups(int count);

    List<KsetRedisRankEntry> groupRange(int startRank, int count);

    Optional<Integer> groupRankOf(String groupId);

    Optional<Double> groupScoreOf(String groupId);

    // ── 二级：组内成员榜 ──

    List<KsetRedisRankEntry> topMembers(String groupId, int count);

    Optional<Integer> memberRankInGroup(String groupId, String memberId);

    Optional<Double> memberScoreInGroup(String groupId, String memberId);

    /**
     * 成员对所属分组贡献累加（更新组内榜 + 分组总榜）。
     *
     * @return 成员在组内的新分值
     */
    double contribute(String groupId, String memberId, double delta);

    double contribute(String groupId, String memberId, double delta, KsetRedisGroupRankChangeListener listener);

    boolean removeMember(String groupId, String memberId);

    boolean removeGroup(String groupId);

    KsetRedisGroupRankBoard addListener(KsetRedisGroupRankChangeListener listener);

    void touchTtl(Duration ttl);
}
