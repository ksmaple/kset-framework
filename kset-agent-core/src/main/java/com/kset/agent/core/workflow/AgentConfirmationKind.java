package com.kset.agent.core.workflow;

import java.util.Arrays;
import java.util.Optional;

/** Agent 等待用户输入的原因。 */
public enum AgentConfirmationKind {
    CLARIFICATION("clarification"),
    SCOPE_SELECTION("scope_selection"),
    TOOL_EXECUTION("tool_execution");

    private final String code;

    AgentConfirmationKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }

    public static Optional<AgentConfirmationKind> fromCode(String value) {
        return Arrays.stream(values()).filter(kind -> kind.matches(value)).findFirst();
    }
}
