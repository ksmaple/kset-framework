package com.kset.agent.core.model;

/** Optional model capability for checking the provider's actual input token window. */
public interface AgentModelCapacity extends AgentModel {

    /** Counts the fully constructed provider request and reserves its required output tokens. */
    ModelPromptCapacity promptCapacity(ModelRequest request);
}
