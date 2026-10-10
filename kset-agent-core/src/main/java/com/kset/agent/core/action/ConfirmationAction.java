package com.kset.agent.core.action;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Map;

public record ConfirmationAction(
        String confirmationId,
        String message,
        Map<String, Object> options) implements AgentAction {

    public ConfirmationAction {
        if (confirmationId == null || confirmationId.isBlank()) {
            throw new IllegalArgumentException("confirmationId must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("confirmation message must not be blank");
        }
        options = AgentValueSnapshot.map(options);
    }

    @Override
    public String actionType() {
        return StandardActionTypes.CONFIRMATION;
    }
}
