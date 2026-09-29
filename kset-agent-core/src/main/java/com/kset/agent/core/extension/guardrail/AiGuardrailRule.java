package com.kset.agent.core.extension.guardrail;

import java.util.Map;

public interface AiGuardrailRule {
    String code();

    default int order() {
        return 0;
    }

    default String sanitizeInput(String input) {
        return input;
    }

    default String sanitizeOutput(String output) {
        return output;
    }

    Map<String, Object> inspect(String text, boolean input);
}
