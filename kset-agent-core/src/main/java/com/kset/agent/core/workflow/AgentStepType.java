package com.kset.agent.core.workflow;

/** Agent 执行轨迹中的内部步骤类型。 */
public enum AgentStepType {
    MODEL_CALL("model_call"),
    PLAN("plan"),
    INCOMPLETE("incomplete"),
    ERROR("error");

    private final String code;

    AgentStepType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }
}
