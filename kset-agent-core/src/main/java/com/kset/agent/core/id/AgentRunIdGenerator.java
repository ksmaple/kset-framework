package com.kset.agent.core.id;

/** Thread-safe generator for globally unique Agent run identifiers. */
@FunctionalInterface
public interface AgentRunIdGenerator {

    String nextAgentRunId();
}
