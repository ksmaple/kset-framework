package com.kset.redis.rank;

/**
 * 排行榜分值排序方向（名次 1 指向“更优”一侧）。
 */
public enum KsetRedisRankOrder {

    /** 分值越高名次越靠前（默认，如积分榜） */
    HIGH_SCORE_FIRST,

    /** 分值越低名次越靠前（如耗时榜） */
    LOW_SCORE_FIRST
}
