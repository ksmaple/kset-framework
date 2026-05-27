package com.kset.redis.key;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis Key 统一规范与生成（段之间使用 {@value #SEPARATOR} 分隔）。
 * <p>
 * 对齐 cache-spec K001：{@code {system}:{module}:{business}:{identifier}}。
 * <ul>
 *   <li>每段非空，且不得包含 {@link #SEPARATOR}</li>
 *   <li>推荐通过 {@link #builder(String)} 或领域方法 {@link #cache} / {@link #rank} / {@link #lock} 构造</li>
 *   <li>与 {@code kset.redis.key-prefix} 组合时使用 {@link #joinPrefix(String, String...)}</li>
 * </ul>
 */
public final class KsetRedisKeys {

    public static final char SEPARATOR = ':';

    private KsetRedisKeys() {
    }

    public static KsetRedisKeyBuilder builder() {
        return new KsetRedisKeyBuilder();
    }

    public static KsetRedisKeyBuilder builder(String system) {
        return new KsetRedisKeyBuilder().segment(requireSegment(system, "system"));
    }

    /**
     * 缓存 Key：{@code {system}:cache:{module}:{business}:{id}}。
     */
    public static String cache(String system, String module, String business, String identifier) {
        return builder(system)
                .segment(KsetRedisKeyNamespace.CACHE)
                .segment(module)
                .segment(business)
                .segment(identifier)
                .build();
    }

    /**
     * 排行榜榜根 Key：{@code {prefix}rank:{boardId}} 或显式 {@code system:rank:boardId}。
     */
    public static String rank(String system, String boardId) {
        return builder(system)
                .segment(KsetRedisKeyNamespace.RANK)
                .segment(boardId)
                .build();
    }

    /**
     * 分布式锁 Key：{@code {system}:lock:{name}}。
     */
    public static String lock(String system, String lockName) {
        return builder(system)
                .segment(KsetRedisKeyNamespace.LOCK)
                .segment(lockName)
                .build();
    }

    /**
     * 将多段用 {@link #SEPARATOR} 连接为完整 key。
     */
    public static String join(String... segments) {
        if (segments == null || segments.length == 0) {
            throw new IllegalArgumentException("redis key segments must not be empty");
        }
        List<String> parts = Arrays.stream(segments)
                .map(s -> requireSegment(s, "segment"))
                .collect(Collectors.toList());
        return String.join(String.valueOf(SEPARATOR), parts);
    }

    /**
     * 在已有 key 后追加段：{@code base:suffix1:suffix2}（{@code base} 可为完整 key）。
     */
    public static String append(String base, String... suffixSegments) {
        if (!StringUtils.hasText(base)) {
            throw new IllegalArgumentException("redis key base must not be blank");
        }
        if (suffixSegments == null || suffixSegments.length == 0) {
            return normalizeKey(base.trim());
        }
        StringBuilder sb = new StringBuilder(base.trim());
        for (String suffix : suffixSegments) {
            sb.append(SEPARATOR).append(requireSegment(suffix, "suffix"));
        }
        return normalizeKey(sb.toString());
    }

    /**
     * 模板 key 前缀 + 逻辑 key（逻辑 key 可已含 {@link #SEPARATOR}）。
     * <p>
     * 若逻辑 key 已以规范化后的 prefix 开头则原样返回。
     */
    public static String joinPrefix(String prefix, String logicalKey) {
        if (!StringUtils.hasText(logicalKey)) {
            throw new IllegalArgumentException("logical redis key must not be blank");
        }
        String logical = logicalKey.trim();
        if (!StringUtils.hasText(prefix)) {
            return normalizeKey(logical);
        }
        String normalizedPrefix = normalizeTrailingColon(prefix);
        if (logical.startsWith(normalizedPrefix) || logical.equals(normalizedPrefix.substring(0, normalizedPrefix.length() - 1))) {
            return normalizeKey(logical);
        }
        if (logical.startsWith(String.valueOf(SEPARATOR))) {
            return normalizeKey(normalizedPrefix.substring(0, normalizedPrefix.length() - 1) + logical);
        }
        return normalizeKey(normalizedPrefix + logical);
    }

    /**
     * 前缀 + 多段逻辑 key（段内不得含 {@link #SEPARATOR}）。
     */
    public static String joinPrefixSegments(String prefix, String... logicalSegments) {
        return joinPrefix(prefix, join(logicalSegments));
    }

    /**
     * 规范化前缀：非空则保证以 {@link #SEPARATOR} 结尾。
     */
    public static String normalizeTrailingColon(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String trimmed = prefix.trim();
        while (trimmed.endsWith(String.valueOf(SEPARATOR))) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "" : trimmed + SEPARATOR;
    }

    /**
     * 去掉首尾多余的 {@link #SEPARATOR}，并合并连续分隔符（用于校验/清洗）。
     */
    public static String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("redis key must not be blank");
        }
        String[] parts = key.split(String.valueOf(SEPARATOR));
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                segments.add(part.trim());
            }
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("redis key must contain at least one segment");
        }
        for (String segment : segments) {
            validateSegment(segment, "segment");
        }
        return String.join(String.valueOf(SEPARATOR), segments);
    }

    static String requireSegment(String value, String name) {
        validateSegment(value, name);
        return value.trim();
    }

    static void validateSegment(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("redis key " + name + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException(
                    "redis key " + name + " must not contain '" + SEPARATOR + "': " + trimmed);
        }
    }
}
