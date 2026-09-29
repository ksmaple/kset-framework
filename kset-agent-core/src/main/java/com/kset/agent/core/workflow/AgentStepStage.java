package com.kset.agent.core.workflow;

/** Agent 单个执行步骤的展示阶段。 */
public enum AgentStepStage {
    MODEL("model"),
    THINKING("thinking"),
    ACTION("action"),
    TOOL("tool"),
    FINAL("final"),
    ERROR("error"),
    INCOMPLETE("incomplete"),
    WAITING_INPUT("waiting_input");

    private final String code;

    AgentStepStage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
