package com.kset.agent.core.action;

/** Executes one protocol-neutral action. */
public interface AgentActionHandler {

    String actionType();

    AgentActionResult handle(AgentAction action, AgentActionContext context);
}
