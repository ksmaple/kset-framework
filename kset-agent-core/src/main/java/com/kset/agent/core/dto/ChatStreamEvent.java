package com.kset.agent.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 聊天流式事件：SSE 推送单元。
 *
 * <p>类型说明：
 * <ul>
 *   <li>{@code step}：Agent 工作流步骤进度（payload 为 {@link AgentWorkflowResult.Step}）</li>
 *   <li>{@code task}：当前可取消的工作流任务 ID</li>
 *   <li>{@code status}：任务级状态（payload 为 TaskStatus 名称）</li>
 *   <li>{@code done}：最终响应摘要（完整步骤已由 step 增量发送，恢复接口仍返回权威快照）</li>
 *   <li>{@code error}：错误信息（payload 为 String）</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class ChatStreamEvent {

    public static final String TYPE_STEP = "step";
    public static final String TYPE_TASK = "task";
    public static final String TYPE_STATUS = "status";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";

    private String type;
    private Object payload;

    public static ChatStreamEvent step(AgentWorkflowResult.Step step) {
        return new ChatStreamEvent(TYPE_STEP, step);
    }

    public static ChatStreamEvent task(String taskId) {
        return new ChatStreamEvent(TYPE_TASK, taskId);
    }

    public static ChatStreamEvent status(String status) {
        return new ChatStreamEvent(TYPE_STATUS, status);
    }

    public static ChatStreamEvent done(Object response) {
        return new ChatStreamEvent(TYPE_DONE, response);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent(TYPE_ERROR, message);
    }
}
