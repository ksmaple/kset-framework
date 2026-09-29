package com.kset.agent.core.context;

/**
 * 当前 Agent 执行线程的运行标识上下文。
 */
public final class AgentExecutionContext {

    private static final ThreadLocal<String> TASK_ID_HOLDER = new ThreadLocal<>();

    private AgentExecutionContext() {
    }

    public static Scope withTaskId(String taskId) {
        String previousTaskId = TASK_ID_HOLDER.get();
        if (taskId == null || taskId.isBlank()) {
            TASK_ID_HOLDER.remove();
        } else {
            TASK_ID_HOLDER.set(taskId);
        }
        return () -> restoreTaskId(previousTaskId);
    }

    public static String currentTaskId() {
        return TASK_ID_HOLDER.get();
    }

    private static void restoreTaskId(String taskId) {
        if (taskId == null) {
            TASK_ID_HOLDER.remove();
        } else {
            TASK_ID_HOLDER.set(taskId);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
