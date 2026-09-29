package com.kset.agent.core.workflow;

/** 当前编排阶段允许模型返回的协议动作。 */
public enum AgentAllowedAction {
    TASK_PLAN(AgentOutputType.TASK_PLAN),
    TOOL_CALL(AgentOutputType.TOOL_CALL),
    TOOL_BATCH(AgentOutputType.TOOL_BATCH),
    CONFIRMATION(AgentOutputType.CONFIRMATION),
    ANSWER_CHUNK(AgentOutputType.ANSWER_CHUNK),
    FINAL_ANSWER(AgentOutputType.FINAL_ANSWER);

    private final AgentOutputType outputType;

    AgentAllowedAction(AgentOutputType outputType) {
        this.outputType = outputType;
    }

    public String code() {
        return outputType.code();
    }
}
