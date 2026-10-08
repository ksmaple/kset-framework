package com.kset.agent.core.action;

public record FinalAnswerAction(String answer) implements AgentAction {

    public FinalAnswerAction {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("final answer must not be blank");
        }
    }

    @Override
    public String type() {
        return StandardActionTypes.FINAL_ANSWER;
    }
}
