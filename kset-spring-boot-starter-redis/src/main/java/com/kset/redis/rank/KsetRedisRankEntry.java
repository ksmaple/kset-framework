package com.kset.redis.rank;

/**
 * 榜单中的一条排名记录（名次从 1 开始）。
 */
public record KsetRedisRankEntry(int rank, String member, double score) {
}
