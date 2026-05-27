package com.kset.redis.rank;

/**
 * 排名变化通知（同步回调；耗时逻辑请自行异步处理）。
 */
@FunctionalInterface
public interface KsetRedisRankChangeListener {

    void onRankChanged(KsetRedisRankChangeEvent event);
}
