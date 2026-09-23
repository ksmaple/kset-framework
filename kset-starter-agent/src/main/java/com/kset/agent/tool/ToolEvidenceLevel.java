package com.kset.agent.tool;

import java.util.Locale;

/** 工具结果可支持结论的程度。 */
public enum ToolEvidenceLevel {
    NONE("none", 0),
    CANDIDATE("candidate", 1),
    CONFIRMED("confirmed", 2);

    private final String code;
    private final int rank;

    ToolEvidenceLevel(String code, int rank) {
        this.code = code;
        this.rank = rank;
    }

    public String code() {
        return code;
    }

    public boolean matches(Object value) {
        return value != null && code.equalsIgnoreCase(String.valueOf(value));
    }

    public boolean atLeast(ToolEvidenceLevel required) {
        return rank >= required.rank;
    }

    public static ToolEvidenceLevel fromCode(Object value) {
        if (value == null) {
            return NONE;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        for (ToolEvidenceLevel level : values()) {
            if (level.code.equals(normalized)) {
                return level;
            }
        }
        return NONE;
    }
}
