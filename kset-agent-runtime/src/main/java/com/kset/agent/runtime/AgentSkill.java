package com.kset.agent.runtime;

/** A reusable named tool collection selected explicitly for one invocation. */
public record AgentSkill(String skillId, AgentToolSource toolSource) {

    public AgentSkill {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        skillId = skillId.trim();
        if (toolSource == null) {
            throw new IllegalArgumentException("toolSource must not be null");
        }
    }
}
