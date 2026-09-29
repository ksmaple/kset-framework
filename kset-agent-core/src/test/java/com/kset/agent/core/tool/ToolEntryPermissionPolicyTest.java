package com.kset.agent.core.tool;

import com.kset.agent.core.config.AgentOrchestrationProperties;
import com.kset.agent.core.spi.AgentAuthContext;
import com.kset.agent.core.spi.AgentUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolEntryPermissionPolicyTest {

    private static final String PERMISSION = "ai:agent:use";

    @AfterEach
    void tearDown() {
        AgentAuthContext.clear();
    }

    private static ToolEntryPermissionPolicy policy(String toolEntryPermission) {
        AgentOrchestrationProperties properties = new AgentOrchestrationProperties();
        properties.setToolEntryPermission(toolEntryPermission);
        return new ToolEntryPermissionPolicy(properties);
    }

    @Test
    void blankPermissionMeansNoCheck() {
        assertTrue(policy("").canUseTools());
        assertTrue(policy(null).canUseTools());
        assertTrue(policy("   ").canUseTools());
    }

    @Test
    void canUseDelegatesToCanUseTools() {
        ToolEntryPermissionPolicy policy = policy("");
        assertTrue(policy.canUse("anyTool"));

        ToolEntryPermissionPolicy restricted = policy(PERMISSION);
        assertFalse(restricted.canUse("anyTool"));
    }

    @Test
    void configuredPermissionWithoutUserContextIsDenied() {
        assertFalse(policy(PERMISSION).canUseTools());
    }

    @Test
    void configuredPermissionWithMatchingUserPermissionIsAllowed() {
        AgentUserSession session = new AgentUserSession();
        session.setUserId(1L);
        session.setPermissions(Set.of(PERMISSION));
        AgentAuthContext.setCurrentUser(session);

        assertTrue(policy(PERMISSION).canUseTools());
    }

    @Test
    void configuredPermissionWithDifferentUserPermissionIsDenied() {
        AgentUserSession session = new AgentUserSession();
        session.setUserId(1L);
        session.setPermissions(Set.of("other:permission"));
        AgentAuthContext.setCurrentUser(session);

        assertFalse(policy(PERMISSION).canUseTools());
    }

    @Test
    void superAdminBypassesPermissionCheck() {
        AgentUserSession session = new AgentUserSession();
        session.setUserId(1L);
        session.setSuperAdmin(true);
        AgentAuthContext.setCurrentUser(session);

        assertTrue(policy(PERMISSION).canUseTools());
    }

    @Test
    void clearedContextIsDeniedAgain() {
        AgentUserSession session = new AgentUserSession();
        session.setUserId(1L);
        session.setPermissions(Set.of(PERMISSION));
        AgentAuthContext.setCurrentUser(session);

        ToolEntryPermissionPolicy policy = policy(PERMISSION);
        assertTrue(policy.canUseTools());

        AgentAuthContext.clear();
        assertFalse(policy.canUseTools());
    }
}
