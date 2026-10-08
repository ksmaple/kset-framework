package com.kset.agent.core.action;

import com.kset.agent.core.AgentCoreException;

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
            String type = normalize(handler.actionType());
            if (type.isEmpty() || values.putIfAbsent(type, handler) != null) {
                throw new IllegalArgumentException("duplicate or blank action handler type: " + type);
            }
        }
        this.handlers = Map.copyOf(values);
    }

    public AgentActionResult dispatch(AgentAction action, AgentActionContext context) {
        AgentActionHandler handler = handlers.get(normalize(action.type()));
        if (handler == null) {
            throw new AgentCoreException("no action handler registered for type: " + action.type());
        }
        return handler.handle(action, context);
    }

    public boolean supports(String type) {
        return handlers.containsKey(normalize(type));
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
