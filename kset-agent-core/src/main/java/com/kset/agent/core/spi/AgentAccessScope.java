package com.kset.agent.core.spi;

import java.util.List;

/**
 * 请求级资源访问范围（在请求边界计算一次，执行期间不可变）。
 */
public record AgentAccessScope(
        String source,
        Long projectId,
        List<Long> availableProjectIds,
        List<Long> availableFolderIds
) {
    public AgentAccessScope {
        availableProjectIds = availableProjectIds == null ? null : List.copyOf(availableProjectIds);
        availableFolderIds = availableFolderIds == null ? null : List.copyOf(availableFolderIds);
    }

    public static AgentAccessScope user() {
        return new AgentAccessScope("USER", null, null, null);
    }

    public static AgentAccessScope mcp(Long projectId, List<Long> projectIds, List<Long> folderIds) {
        return new AgentAccessScope("MCP", projectId, projectIds, folderIds);
    }

    public boolean isMcp() {
        return "MCP".equals(source);
    }
}
