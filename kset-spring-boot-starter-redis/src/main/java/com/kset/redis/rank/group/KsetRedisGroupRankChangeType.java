package com.kset.redis.rank.group;

/**
 * 分组榜变更维度。
 */
public enum KsetRedisGroupRankChangeType {

    /** 分组（战队/公会）在总榜的名次变化 */
    GROUP,

    /** 成员在所属分组内的名次变化 */
    MEMBER_IN_GROUP
}
