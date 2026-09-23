package com.kset.agent.memory;

import java.util.Locale;

/** 模型建议的记忆保留优先级；历史数据统一按 MEDIUM 兼容。 */
public enum MemoryPriority {
    HIGH(3),
    MEDIUM(2),
    LOW(1),
    DISCARDABLE(0);

    private final int weight;

    MemoryPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public static MemoryPriority fromCode(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MEDIUM;
        }
    }
}
