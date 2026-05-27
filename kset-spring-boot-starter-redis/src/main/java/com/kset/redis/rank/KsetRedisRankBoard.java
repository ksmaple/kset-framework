package com.kset.redis.rank;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 单维度排行榜（Redis ZSET）。
 * <p>
 * 基础能力：TopN、成员名次、累加分、排名变化通知。
 */
public interface KsetRedisRankBoard {

    String boardId();

    KsetRedisRankOrder order();

    /**
     * 分值最高（或 {@link KsetRedisRankOrder#LOW_SCORE_FIRST} 时最低）的前 {@code count} 名。
     */
    List<KsetRedisRankEntry> top(int count);

    /**
     * 分页：从第 {@code startRank} 名开始（含，1-based），取 {@code count} 条。
     */
    List<KsetRedisRankEntry> range(int startRank, int count);

    /**
     * 成员当前名次（1-based）；不在榜返回 empty。
     */
    Optional<Integer> rankOf(String member);

    Optional<Double> scoreOf(String member);

    Optional<KsetRedisRankEntry> entryOf(String member);

    /**
     * 累加分值并返回新分值；若名次变化则触发监听器。
     */
    double increment(String member, double delta);

    double increment(String member, double delta, KsetRedisRankChangeListener listener);

    /**
     * 覆盖设置分值（存在则更新）。
     */
    boolean setScore(String member, double score);

    boolean setScore(String member, double score, KsetRedisRankChangeListener listener);

    boolean remove(String member);

    long size();

    void clear();

    /**
     * 注册榜单级监听器（对该榜所有写操作生效）。
     */
    KsetRedisRankBoard addListener(KsetRedisRankChangeListener listener);

    /**
     * 刷新榜单 TTL（若创建时配置了过期时间）。
     */
    void touchTtl(Duration ttl);
}
