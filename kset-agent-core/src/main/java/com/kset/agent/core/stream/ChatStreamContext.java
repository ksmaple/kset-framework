package com.kset.agent.core.stream;

import com.kset.agent.core.dto.ChatStreamEvent;

import java.util.function.Consumer;

/**
 * 聊天流式上下文：在同步聊天链路中传递 SSE 事件发射器。
 *
 * <p>跨模块共享（agent 管道发射、对话控制器绑定），作为 agent 模块 API 面。
 *
 * <p>流式请求由控制器切换到工作线程执行同步管道，通过本 ThreadLocal 让管道深处
 * （最终回答生成、Agent 步骤推进）把进度事件实时发回 SSE 通道；
 * 非流式请求下 {@link #emit} 为空操作，不影响原有同步行为。
 */
public final class ChatStreamContext {

    private static final ThreadLocal<Consumer<ChatStreamEvent>> HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Consumer<String>> TASK_ID_CONSUMER_HOLDER = new ThreadLocal<>();

    private ChatStreamContext() {
    }

    public static boolean isActive() {
        return HOLDER.get() != null;
    }

    public static void emit(ChatStreamEvent event) {
        Consumer<ChatStreamEvent> emitter = HOLDER.get();
        if (emitter != null && event != null) {
            emitter.accept(event);
        }
    }

    public static Consumer<ChatStreamEvent> currentEmitter() {
        return HOLDER.get();
    }

    /**
     * 绑定事件发射器，返回用于 try-with-resources 的关闭句柄（务必在使用后关闭，避免线程池泄漏）。
     */
    public static Scope withEmitter(Consumer<ChatStreamEvent> emitter) {
        Consumer<ChatStreamEvent> previousEmitter = HOLDER.get();
        HOLDER.set(emitter);
        return () -> restore(HOLDER, previousEmitter);
    }

    public static Scope withEmitter(Consumer<ChatStreamEvent> emitter, Consumer<String> taskIdConsumer) {
        Consumer<ChatStreamEvent> previousEmitter = HOLDER.get();
        Consumer<String> previousTaskIdConsumer = TASK_ID_CONSUMER_HOLDER.get();
        HOLDER.set(emitter);
        TASK_ID_CONSUMER_HOLDER.set(taskIdConsumer);
        return () -> {
            restore(HOLDER, previousEmitter);
            restore(TASK_ID_CONSUMER_HOLDER, previousTaskIdConsumer);
        };
    }

    public static void notifyTaskId(String taskId) {
        Consumer<String> consumer = TASK_ID_CONSUMER_HOLDER.get();
        if (consumer != null && taskId != null && !taskId.isBlank()) {
            consumer.accept(taskId);
        }
    }

    private static <T> void restore(ThreadLocal<T> holder, T previousValue) {
        if (previousValue == null) {
            holder.remove();
        } else {
            holder.set(previousValue);
        }
    }

    /** 关闭句柄：close 不抛受检异常，便于 try-with-resources 使用 */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
