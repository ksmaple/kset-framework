package com.kset.agent.core.tool;

import com.kset.agent.core.config.AgentOrchestrationProperties;
import com.kset.agent.core.spi.AgentAuthContext;
import org.springframework.stereotype.Component;

/**
 * Shared functional permission gate for Agent and MCP tool entry points.
 *
 * <p>校验的功能权限点由 {@code ai.agent.orchestration.tool-entry-permission} 配置；
 * 留空表示不校验（公共包默认语义中立，由宿主应用按需配置）。
 */
@Component
public class ToolEntryPermissionPolicy {

    private final AgentOrchestrationProperties orchestrationProperties;

    public ToolEntryPermissionPolicy(AgentOrchestrationProperties orchestrationProperties) {
        this.orchestrationProperties = orchestrationProperties;
    }

    public boolean canUseTools() {
        String permission = orchestrationProperties.getToolEntryPermission();
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return AgentAuthContext.hasPermission(permission);
    }

    /**
     * 兼容旧调用方签名：Agent 工具不是独立授权主体，仅保留问答入口闸门。
     */
    public boolean canUse(String toolName) {
        return canUseTools();
    }
}
