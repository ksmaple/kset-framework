package com.kset.redis.rank.internal;

import com.kset.redis.key.KsetRedisKeys;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 排行榜 Redis key 规则（基于榜根前缀 {@code root}）。
 */
public final class KsetRedisRankKeys {

    private final String root;
    private final String groupRoot;

    private KsetRedisRankKeys(String root) {
        this.root = root;
        this.groupRoot = hasHashTag(root) ? root : "{kset-rank-" + rootHash(root) + "}:" + root;
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
        return KsetRedisKeys.append(groupRoot, "groups");
    }

    public String groupMembersKey(String groupId) {
        return KsetRedisKeys.append(groupRoot, "g", sanitize(groupId), "members");
    }

    public String legacyGroupTotalKey() {
        return KsetRedisKeys.append(root, "groups");
    }

    private static boolean hasHashTag(String key) {
        int open = key.indexOf('{');
        int close = open >= 0 ? key.indexOf('}', open + 1) : -1;
        return close > open + 1;
    }

    private static String rootHash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String sanitize(String id) {
        return KsetRedisKeys.requireSegment(id, "groupId");
    }
}
