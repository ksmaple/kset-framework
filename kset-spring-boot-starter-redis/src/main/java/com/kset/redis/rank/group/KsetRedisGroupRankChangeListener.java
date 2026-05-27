package com.kset.redis.rank.group;

/**
 * 分组榜排名变化通知。
 */
@FunctionalInterface
public interface KsetRedisGroupRankChangeListener {

    void onRankChanged(KsetRedisGroupRankChangeEvent event);
}
