package com.kset.agent.core.action;

import java.util.LinkedHashMap;
import java.util.Map;

public record ConfirmationAction(
        String confirmationId,
        String message,
        Map<String, Object> options) implements AgentAction {

    public ConfirmationAction {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("confirmation message must not be blank");
        }
        confirmationId = confirmationId == null ? "" : confirmationId;
        options = options == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(options));
    }

    @Override
    public String type() {
        return StandardActionTypes.CONFIRMATION;
    }
}
