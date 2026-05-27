package com.kset.redis.rank;

/**
 * 成员排名或分值变化事件（在 {@link KsetRedisRankBoard#increment} 等写操作后触发）。
 */
public record KsetRedisRankChangeEvent(
        String boardId,
        String member,
        Double previousScore,
        double newScore,
        Integer previousRank,
        int newRank,
        boolean rankChanged) {

    public boolean isNewMember() {
        return previousRank == null;
    }
}
