package com.kset.redis.rank.internal;

import com.kset.redis.key.KsetRedisKeys;
import org.springframework.util.StringUtils;

/**
 * 排行榜 Redis key 规则（基于榜根前缀 {@code root}）。
 */
public final class KsetRedisRankKeys {

    private final String root;

    private KsetRedisRankKeys(String root) {
        this.root = root;
    }

    public static KsetRedisRankKeys forRoot(String root) {
        if (!StringUtils.hasText(root)) {
            throw new IllegalArgumentException("rank root key must not be blank");
        }
        return new KsetRedisRankKeys(KsetRedisKeys.normalizeKey(root));
    }

    /**
     * 单榜 ZSET key（与 root 相同）。
     */
    public String singleBoardKey() {
        return root;
    }

    public String groupTotalKey() {
        return KsetRedisKeys.append(root, "groups");
    }

    public String groupMembersKey(String groupId) {
        return KsetRedisKeys.append(root, "g", sanitize(groupId), "members");
    }

    private static String sanitize(String id) {
        return KsetRedisKeys.requireSegment(id, "groupId");
    }
}
