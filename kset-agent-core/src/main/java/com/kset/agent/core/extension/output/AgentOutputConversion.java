package com.kset.agent.core.extension.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.kset.agent.core.dto.AgentModelOutput;

public record AgentOutputConversion(JsonNode json, AgentModelOutput output, String error) {
    public boolean valid() {
        return error == null;
    }
}
