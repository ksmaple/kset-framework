package com.kset.agent.core.action;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable action-handler registry used by the loop kernel. */
public final class AgentActionRegistry {

    private final Map<String, AgentActionHandler> handlers;

    public AgentActionRegistry(List<AgentActionHandler> handlers) {
        Map<String, AgentActionHandler> values = new LinkedHashMap<>();
        for (AgentActionHandler handler : handlers == null ? List.<AgentActionHandler>of() : handlers) {
            String actionType = normalize(handler.actionType());
            if (actionType.isEmpty() || values.putIfAbsent(actionType, handler) != null) {
                throw new IllegalArgumentException(
                        "duplicate or blank action handler actionType: " + actionType);
            }
        }
        this.handlers = Map.copyOf(values);
    }

    public AgentActionResult dispatch(AgentAction action, AgentActionContext context) {
        AgentActionHandler handler = handlers.get(normalize(action.actionType()));
        if (handler == null) {
            throw new AgentCoreException(AgentErrorCode.ACTION_NOT_REGISTERED,
                    "no action handler registered for actionType: " + action.actionType());
        }
        return handler.handle(action, context);
    }

    public boolean supports(String actionType) {
        return handlers.containsKey(normalize(actionType));
    }

    private static String normalize(String actionType) {
        return actionType == null ? "" : actionType.trim().toLowerCase(Locale.ROOT);
    }
}
