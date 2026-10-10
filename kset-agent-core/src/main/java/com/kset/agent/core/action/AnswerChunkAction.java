package com.kset.agent.core.action;

public record AnswerChunkAction(String text) implements AgentAction {

    public AnswerChunkAction {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("answer chunk must not be blank");
        }
    }

    @Override
    public String actionType() {
        return StandardActionTypes.ANSWER_CHUNK;
    }
}
