package com.kset.agent.workflow;

import java.util.Arrays;
import java.util.Optional;

/** Agent 用户确认交互方式。 */
public enum AgentConfirmationSelectionType {
    SINGLE,
    MULTIPLE,
    INPUT,
    CONFIRM;

    public static Optional<AgentConfirmationSelectionType> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(type -> type.name().equalsIgnoreCase(value.trim())).findFirst();
    }
}
