package com.kset.agent.core.id;

/** Thread-safe generator for globally unique agent identifiers. */
@FunctionalInterface
public interface AgentIdGenerator {

    String nextId();
}
