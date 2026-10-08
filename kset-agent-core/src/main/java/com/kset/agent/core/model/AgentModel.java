package com.kset.agent.core.model;

/** Model boundary used by the loop. Providers adapt their own SDKs to this interface. */
@FunctionalInterface
public interface AgentModel {

    ModelResponse generate(ModelRequest request);
}
