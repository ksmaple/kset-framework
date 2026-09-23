package com.kset.agent.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版工具注册中心默认实现。
 *
 * <p>宿主应用具备多来源工具（内置/MCP/数据源等）时应提供自定义 {@link ToolRegistry}
 * Bean 覆盖本默认实现（默认实现由 KsetAgentAutoConfiguration 按 @ConditionalOnMissingBean 注册）。
 */
public class InMemoryToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    @Override
    public void register(ToolDefinition tool) {
        if (tool != null && tool.getName() != null) {
            tools.put(tool.getName(), tool);
        }
    }

    @Override
    public void unregister(String toolName) {
        if (toolName != null) {
            tools.remove(toolName);
        }
    }

    @Override
    public ToolDefinition get(String toolName) {
        return toolName == null ? null : tools.get(toolName);
    }

    @Override
    public List<ToolDefinition> listAll() {
        return tools.values().stream().filter(ToolDefinition::isEnabled).toList();
    }

    @Override
    public List<ToolDefinition> listByCategory(String category) {
        return tools.values().stream()
                .filter(ToolDefinition::isEnabled)
                .filter(tool -> category == null || category.equals(tool.getCategory()))
                .toList();
    }
}
