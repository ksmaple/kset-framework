package com.kset.redis.key;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式构造 Redis Key（段之间 {@link KsetRedisKeys#SEPARATOR} 连接）。
 */
public final class KsetRedisKeyBuilder {

    private final List<String> segments = new ArrayList<>();

    public KsetRedisKeyBuilder segment(String part) {
        segments.add(KsetRedisKeys.requireSegment(part, "segment"));
        return this;
    }

    public KsetRedisKeyBuilder id(String identifier) {
        return segment(identifier);
    }

    public String build() {
        if (segments.isEmpty()) {
            throw new IllegalStateException("redis key must have at least one segment");
        }
        return KsetRedisKeys.join(segments.toArray(new String[0]));
    }
}
