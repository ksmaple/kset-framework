package com.kset.agent.tool;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 工具定义：描述一个可被 Agent 调用的工具
 */
public class ToolDefinition {

    private String name;
    private String description;
    private String category;
    private String source;
    private List<ToolParameter> parameters;
    private boolean enabled;
    private int priority;
    private boolean readOnly = true;
    private boolean requiresConfirmation = false;
    /** 是否对 MCP 客户端暴露（tools/list）；与站内 Agent 共用同一份描述与 inputSchema */
    private boolean mcpExposed = false;
    private boolean projectScoped = false;
    private boolean directAnswer = false;
    private String resourceType;
    private String operation;
    private String evidenceLevel = ToolEvidenceLevel.CANDIDATE.code();
    private String cost = "low";
    private String resultSchema = "text-v1";
    private List<String> nextCapabilities = List.of();
    private Map<String, Object> inputSchema;
    private Function<Map<String, Object>, Object> executor;

    public ToolDefinition() {
    }

    public ToolDefinition(String name, String description, String category, String source,
                          List<ToolParameter> parameters, Function<Map<String, Object>, Object> executor) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.source = source;
        this.parameters = parameters;
        this.executor = executor;
        this.enabled = true;
        this.priority = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<ToolParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<ToolParameter> parameters) {
        this.parameters = parameters;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Function<Map<String, Object>, Object> getExecutor() {
        return executor;
    }

    public void setExecutor(Function<Map<String, Object>, Object> executor) {
        this.executor = executor;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    public boolean isMcpExposed() {
        return mcpExposed;
    }

    public void setMcpExposed(boolean mcpExposed) {
        this.mcpExposed = mcpExposed;
    }

    public boolean isProjectScoped() {
        return projectScoped;
    }

    public void setProjectScoped(boolean projectScoped) {
        this.projectScoped = projectScoped;
    }

    public boolean isDirectAnswer() {
        return directAnswer;
    }

    public void setDirectAnswer(boolean directAnswer) {
        this.directAnswer = directAnswer;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getEvidenceLevel() {
        return evidenceLevel;
    }

    public void setEvidenceLevel(String evidenceLevel) {
        this.evidenceLevel = evidenceLevel;
    }

    public String getCost() {
        return cost;
    }

    public void setCost(String cost) {
        this.cost = cost;
    }

    public String getResultSchema() {
        return resultSchema;
    }

    public void setResultSchema(String resultSchema) {
        this.resultSchema = resultSchema;
    }

    public List<String> getNextCapabilities() {
        return nextCapabilities;
    }

    public void setNextCapabilities(List<String> nextCapabilities) {
        this.nextCapabilities = nextCapabilities != null ? List.copyOf(nextCapabilities) : List.of();
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }
}
