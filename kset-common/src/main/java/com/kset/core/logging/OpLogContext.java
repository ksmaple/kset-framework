package com.kset.core.logging;

/**
 * 操作日志上下文（线程局部）。
 */
public final class OpLogContext {

    private static final ThreadLocal<String> OPERATOR = new ThreadLocal<>();

    private OpLogContext() {
    }

    public static void setOperator(String operator) {
        if (operator != null && !operator.isBlank()) {
            OPERATOR.set(operator);
        }
    }

    public static String getOperator() {
        return OPERATOR.get();
    }

    public static void clear() {
        OPERATOR.remove();
    }
}
