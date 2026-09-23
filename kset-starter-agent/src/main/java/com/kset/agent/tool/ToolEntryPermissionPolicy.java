package com.kset.agent.tool;

import com.kset.agent.spi.AgentAuthContext;
import org.springframework.stereotype.Component;

/** Shared functional permission gate for Agent and MCP tool entry points. */
@Component
public class ToolEntryPermissionPolicy {

    private static final String TOOL_ENTRY_PERMISSION = "rag:chat";

    public boolean canUseTools() {
        return AgentAuthContext.hasPermission(TOOL_ENTRY_PERMISSION);
    }

    /**
     * 兼容旧调用方签名：Agent 工具不是独立授权主体，仅保留问答入口闸门。
     */
    public boolean canUse(String toolName) {
        return canUseTools();
    }
}
