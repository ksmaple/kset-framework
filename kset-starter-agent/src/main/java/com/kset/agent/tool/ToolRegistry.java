package com.kset.agent.tool;

import java.util.List;

/**
 * 工具注册中心：统一管理内置、MCP、数据源、自定义工具
 */
public interface ToolRegistry {

    /**
     * 注册工具
     */
    void register(ToolDefinition tool);

    /**
     * 注销工具
     */
    void unregister(String toolName);

    /**
     * 根据名称获取工具
     */
    ToolDefinition get(String toolName);

    /**
     * 列出所有已启用工具
     */
    List<ToolDefinition> listAll();

    /**
     * 列出指定类别工具
     */
    List<ToolDefinition> listByCategory(String category);
}
