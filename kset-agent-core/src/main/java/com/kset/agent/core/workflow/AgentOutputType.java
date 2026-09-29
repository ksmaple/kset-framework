package com.kset.agent.core.workflow;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Agent 模型输出动作类型。 */
public enum AgentOutputType {
    TASK_PLAN("task_plan"),
    TOOL_CALL("tool_call"),
    TOOL_BATCH("tool_batch"),
    ANSWER_CHUNK("answer_chunk"),
    FINAL_ANSWER("final_answer"),
    CONFIRMATION("confirmation");

    private final String code;

    AgentOutputType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }

    public static Optional<AgentOutputType> fromCode(String value) {
        return Arrays.stream(values()).filter(type -> type.matches(value)).findFirst();
    }

    public static Set<String> codes() {
        return Arrays.stream(values()).map(AgentOutputType::code).collect(Collectors.toUnmodifiableSet());
    }
}
