package com.kset.agent.core.tool;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * 工具执行的内部结构化结果，避免根据展示文本推断执行状态。
 */
public record ToolExecutionOutcome(
        boolean executionSuccess,
        boolean hasEvidence,
        Object data,
        String errorCode,
        String errorMessage) {

    public static ToolExecutionOutcome success(Object data) {
        return new ToolExecutionOutcome(true, containsEvidence(data), data, null, null);
    }

    public static ToolExecutionOutcome withoutEvidence(Object data) {
        return new ToolExecutionOutcome(true, false, data, null, null);
    }

    public static ToolExecutionOutcome failure(String errorCode, String errorMessage) {
        return new ToolExecutionOutcome(false, false, null, errorCode, errorMessage);
    }

    private static boolean containsEvidence(Object data) {
        if (data == null) {
            return false;
        }
        if (data instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        if (data instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (data instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (data.getClass().isArray()) {
            return Array.getLength(data) > 0;
        }
        return true;
    }
}
