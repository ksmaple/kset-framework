package com.kset.agent.model;

/**
 * 工具规格描述（领域层抽象）
 *
 * <p>描述 AI Agent 可调用的外部工具，与具体 AI 框架无关。
 * 底层实现负责将其转换为对应框架的工具描述格式（如 LangChain4j 的 ToolSpecification）。
 */
public class ToolSpec {

    private final String name;
    private final String description;

    public ToolSpec(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
