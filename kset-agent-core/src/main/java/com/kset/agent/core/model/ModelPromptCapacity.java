package com.kset.agent.core.model;

/** Provider input token usage and the input allowance after output tokens are reserved. */
public record ModelPromptCapacity(long inputTokens, long maxInputTokens) {

    public ModelPromptCapacity {
        if (inputTokens < 0 || maxInputTokens < 0) {
            throw new IllegalArgumentException("model prompt token counts must not be negative");
        }
    }

    public boolean exceedsLimit() {
        return inputTokens > maxInputTokens;
    }
}
