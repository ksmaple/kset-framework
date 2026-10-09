package com.kset.agent.core.action;

/** Executes one protocol-neutral action. Implementations shared by a kernel must be thread-safe. */
public interface AgentActionHandler {

    String actionType();

    AgentActionResult handle(AgentAction action, AgentActionContext context);
}
