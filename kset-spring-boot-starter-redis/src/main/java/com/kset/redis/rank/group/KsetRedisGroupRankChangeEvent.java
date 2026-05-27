package com.kset.redis.rank.group;

/**
 * 分组榜排名变化事件。
 */
public record KsetRedisGroupRankChangeEvent(
        String boardId,
        KsetRedisGroupRankChangeType changeType,
        String groupId,
        String memberId,
        Double previousScore,
        double newScore,
        Integer previousRank,
        int newRank,
        boolean rankChanged) {

    public boolean isNewEntry() {
        return previousRank == null;
    }
}
