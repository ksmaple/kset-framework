package com.kset.agent.runtime;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Trusted, invocation-scoped business facts supplied by the host. This is not an authorization grant. */
public record AgentBusinessContext(
        String principalId,
        String tenantId,
        Set<String> roles,
        Map<String, Object> attributes) {

    public AgentBusinessContext {
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId must not be blank");
        }
        principalId = principalId.trim();
        tenantId = tenantId == null || tenantId.isBlank() ? null : tenantId.trim();
        Set<String> copiedRoles = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                if (role == null || role.isBlank()) {
                    throw new IllegalArgumentException("roles must not contain blank values");
                }
                copiedRoles.add(role.trim());
            }
        }
        roles = Set.copyOf(copiedRoles);
        attributes = AgentValueSnapshot.map(attributes);
    }

    @Override
    public String toString() {
        return "AgentBusinessContext[redacted]";
    }
}
