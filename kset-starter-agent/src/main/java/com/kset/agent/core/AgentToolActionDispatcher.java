package com.kset.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.context.AgentExecutionContext;
import com.kset.agent.dto.ToolObservation;
import com.kset.agent.port.ToolCallMetricsPort;
import com.kset.agent.tool.ToolEntryPermissionPolicy;
import com.kset.agent.spi.ProjectAccessPort;
import com.kset.agent.spi.CodeRepositoryRef;
import com.kset.agent.spi.CodeRepositoryAccessPort;
import com.kset.agent.tool.ToolDefinition;
import com.kset.agent.tool.ToolEvidenceLevel;
import com.kset.agent.tool.ToolExecutionException;
import com.kset.agent.tool.ToolExecutionOutcome;
import com.kset.agent.tool.ToolRegistry;
import com.kset.agent.spi.AgentAuthContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class AgentToolActionDispatcher {

    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<>() {};

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ToolCallMetricsPort toolCallMetricsRecorder;
    private final CodeRepositoryAccessPort codeRepositoryRepository;
    private final ProjectAccessPort projectMemberApplicationService;
    private final ToolEntryPermissionPolicy toolPermissionService;

    public AgentToolActionDispatcher(ToolRegistry toolRegistry,
                                     ObjectMapper objectMapper,
                                     ToolCallMetricsPort toolCallMetricsRecorder,
                                     CodeRepositoryAccessPort codeRepositoryRepository,
                                     ProjectAccessPort projectMemberApplicationService,
                                     ToolEntryPermissionPolicy toolPermissionService) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.toolCallMetricsRecorder = toolCallMetricsRecorder;
        this.codeRepositoryRepository = codeRepositoryRepository;
        this.projectMemberApplicationService = projectMemberApplicationService;
        this.toolPermissionService = toolPermissionService;
    }

    public ToolObservation executeObservation(String action, String actionInput) {
        return executeObservation(action, actionInput, false);
    }

    public ToolObservation executeConfirmedObservation(String action, String actionInput) {
        return executeObservation(action, actionInput, true);
    }

    private ToolObservation executeObservation(String action, String actionInput, boolean confirmed) {
        if (action == null || action.isBlank() || "finish".equalsIgnoreCase(action)) {
            return failedObservation(action, Map.of(), null, "工具名称不能为空");
        }

        String normalized = normalize(action);
        ToolDefinition tool = toolRegistry.get(normalized);
        if (tool == null || tool.getExecutor() == null) {
            String error = "未知工具: " + action;
            recordToolCall(normalized, 0, false, error, AgentAuthContext.getUserId());
            return failedObservation(action, Map.of(), tool, error);
        }

        Long userId = AgentAuthContext.getUserId();
        long start = System.currentTimeMillis();
        Map<String, Object> args;
        try {
            args = parseArgs(actionInput);
        } catch (IllegalArgumentException e) {
            recordToolCall(normalized, System.currentTimeMillis() - start, false, e.getMessage(), userId);
            return failedObservation(action, Map.of(), tool, "INVALID_TOOL_ARGUMENTS", e.getMessage());
        }
        String deniedCode = "TOOL_POLICY_DENIED";
        String deniedReason;
        try {
            deniedReason = preCheckTool(tool, args, confirmed);
        } catch (Exception e) {
            deniedCode = "TOOL_POLICY_CHECK_FAILED";
            deniedReason = "工具运行时策略检查失败: " + e.getMessage();
        }
        if (deniedReason != null) {
            recordToolCall(normalized, System.currentTimeMillis() - start, false, deniedReason, userId);
            return failedObservation(action, args, tool, deniedCode, deniedReason);
        }
        try {
            Object result = tool.getExecutor().apply(args);
            ToolExecutionOutcome outcome = result instanceof ToolExecutionOutcome typed
                    ? typed : ToolExecutionOutcome.success(result);
            boolean success = outcome.executionSuccess();
            String error = success ? null : outcome.errorMessage();
            long durationMs = System.currentTimeMillis() - start;
            recordToolCall(normalized, durationMs, success, error, userId);
            log.info("AI 工具执行完成: tool={}, success={}, durationMs={}, evidenceLevel={}",
                    tool.getName(), success, durationMs,
                    success && outcome.hasEvidence()
                            ? tool.getEvidenceLevel() : ToolEvidenceLevel.NONE.code());
            return observation(action, args, tool, outcome);
        } catch (ToolExecutionException e) {
            recordToolCall(normalized, System.currentTimeMillis() - start, false, e.getMessage(), userId);
            log.warn("Agent 工具执行失败 [{}]: {}", action, e.getMessage());
            return observation(action, args, tool,
                    ToolExecutionOutcome.failure(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            recordToolCall(normalized, System.currentTimeMillis() - start, false, e.getMessage(), userId);
            log.warn("Agent 工具执行失败 [{}]: {}", action, e.getMessage());
            return failedObservation(action, args, tool, "TOOL_EXECUTION_ERROR", e.getMessage());
        }
    }

    private ToolObservation observation(String action, Map<String, Object> args, ToolDefinition tool,
                                        ToolExecutionOutcome outcome) {
        Object displayResult = outcome.executionSuccess() ? outcome.data() : outcome.errorMessage();
        List<Map<String, Object>> evidenceItems = outcome.executionSuccess() && outcome.hasEvidence()
                ? extractEvidenceItems(tool, outcome.data()) : List.of();
        return new ToolObservation(action, args, outcome.executionSuccess(), outcome.hasEvidence(),
                tool.getResourceType(), tool.getOperation(),
                outcome.executionSuccess() && outcome.hasEvidence()
                        ? tool.getEvidenceLevel() : ToolEvidenceLevel.NONE.code(),
                tool.getCost(), tool.getResultSchema(), displayResult,
                evidenceItems, tool.getNextCapabilities(), outcome.errorCode(), outcome.errorMessage());
    }

    private List<Map<String, Object>> extractEvidenceItems(ToolDefinition tool, Object result) {
        if (result == null) {
            return List.of();
        }
        if (tool.getResultSchema() == null || tool.getResultSchema().startsWith("text-")) {
            return buildTextEvidenceItems(result);
        }
        try {
            Map<String, Object> parsed = objectMapper.convertValue(result, JSON_MAP_TYPE);
            for (String key : List.of("evidenceItems", "hits", "files", "repositories", "rows", "documents")) {
                Object items = parsed.get(key);
                if (items instanceof List<?> values && !values.isEmpty()) {
                    return values.stream()
                            .filter(Map.class::isInstance)
                            .map(value -> toStringObjectMap((Map<?, ?>) value))
                            .map(this::compactEvidenceItem)
                            .limit(AgentProtocolDefinition.MAX_EVIDENCE_ITEMS)
                            .toList();
                }
            }
            return List.of(compactEvidenceItem(parsed));
        } catch (Exception e) {
            log.debug("工具结构化结果解析失败，保留原始结果: tool={}, schema={}",
                    tool.getName(), tool.getResultSchema());
            return List.of();
        }
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<String, Object> compactEvidenceItem(Map<String, Object> source) {
        Map<String, Object> compact = new LinkedHashMap<>();
        source.forEach((key, value) -> compact.put(key, compactEvidenceValue(value)));
        return compact;
    }

    private Object compactEvidenceValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return compactEvidenceItem(toStringObjectMap(map));
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .limit(AgentProtocolDefinition.MAX_EVIDENCE_ITEMS)
                    .map(this::compactEvidenceValue)
                    .toList();
        }
        if (!(value instanceof String text)) {
            return value;
        }
        return compactEvidenceText(text);
    }

    private List<Map<String, Object>> buildTextEvidenceItems(Object result) {
        String text = String.valueOf(result);
        List<String> segments = text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .limit(AgentProtocolDefinition.MAX_EVIDENCE_ITEMS)
                .toList();
        if (segments.isEmpty()) {
            segments = List.of(text);
        }
        List<Map<String, Object>> evidenceItems = new java.util.ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("position", i + 1);
            evidence.put("content", compactEvidenceText(segment));
            evidence.put("originalChars", segment.length());
            evidence.put("truncated", segment.length() > AgentProtocolDefinition.MAX_EVIDENCE_FIELD_CHARS);
            evidenceItems.add(evidence);
        }
        return List.copyOf(evidenceItems);
    }

    private String compactEvidenceText(String text) {
        if (text.length() <= AgentProtocolDefinition.MAX_EVIDENCE_FIELD_CHARS) {
            return text;
        }
        String omittedNotice = "...[已省略]";
        int contentLimit = AgentProtocolDefinition.MAX_EVIDENCE_FIELD_CHARS - omittedNotice.length();
        return text.substring(0, contentLimit) + omittedNotice;
    }

    private ToolObservation failedObservation(String action, Map<String, Object> args, ToolDefinition tool,
                                               String error) {
        return failedObservation(action, args, tool, "TOOL_EXECUTION_ERROR", error);
    }

    private ToolObservation failedObservation(String action, Map<String, Object> args, ToolDefinition tool,
                                               String errorCode, String error) {
        if (tool == null) {
            return new ToolObservation(action, args, false, false, "unknown", "unknown",
                    ToolEvidenceLevel.NONE.code(), "low", "text-v1",
                    error, List.of(), List.of(), "UNKNOWN_TOOL", error);
        }
        return observation(action, args, tool, ToolExecutionOutcome.failure(errorCode, error));
    }

    private void recordToolCall(String toolName, long durationMs, boolean success, String errorMsg, Long userId) {
        toolCallMetricsRecorder.record(toolName, AgentExecutionContext.currentTaskId(), durationMs,
                success, errorMsg, userId);
    }

    private String preCheckTool(ToolDefinition tool, Map<String, Object> args, boolean confirmed) {
        if (!toolPermissionService.canUse(tool.getName())) {
            return "当前用户无问答权限";
        }
        if (!tool.isEnabled()) {
            return "工具未对 Agent 开放";
        }
        if (!confirmed && !tool.isReadOnly()) {
            return "非只读工具不能自动执行，需要人工确认";
        }
        if (!confirmed && tool.isRequiresConfirmation()) {
            return "工具需要用户确认后才能执行";
        }
        if (tool.isProjectScoped() && !tool.isReadOnly()
                && (!args.containsKey("projectId") || toLong(args.get("projectId")) == null)) {
            return "项目写操作必须明确提供 projectId 目标项目";
        }
        String category = tool.getCategory() != null ? tool.getCategory() : "";
        if ("code".equals(category) || "git-readonly".equals(category)) {
            return preCheckCodeOrGitTool(tool, args);
        }
        return null;
    }

    private String preCheckCodeOrGitTool(ToolDefinition tool, Map<String, Object> args) {
        if (tool.isProjectScoped() && args.containsKey("projectId")) {
            Object projectId = args.get("projectId");
            if (projectId == null || String.valueOf(projectId).isBlank()) {
                return null;
            }
            Long normalizedProjectId = toLong(projectId);
            if (normalizedProjectId == null) {
                return "projectId 必须是有效的项目 ID";
            }
            return canAccessProject(normalizedProjectId) ? null : "当前用户无权访问项目: " + normalizedProjectId;
        }
        if ("code_repository".equals(tool.getResourceType()) && args.containsKey("repoPath")) {
            Object repoPath = args.get("repoPath");
            if (repoPath == null || String.valueOf(repoPath).isBlank()) {
                return "代码仓库工具必须提供 repoPath";
            }
            Optional<CodeRepositoryRef> repo = findRegisteredRepository(String.valueOf(repoPath));
            if (repo.isEmpty()) {
                return "只能访问仓库管理中已登记的仓库路径";
            }
            Long projectId = repo.get().getProjectId();
            if (projectId == null || !canAccessProject(projectId)) {
                return "当前用户无权访问该仓库所属项目";
            }
        }
        return null;
    }

    private Optional<CodeRepositoryRef> findRegisteredRepository(String repoPath) {
        Path requestedPath = Path.of(repoPath).toAbsolutePath().normalize();
        List<CodeRepositoryRef> repos = codeRepositoryRepository.findAll();
        return repos.stream()
                .filter(repo -> repo.getPath() != null && !repo.getPath().isBlank())
                .filter(repo -> requestedPath.equals(Path.of(repo.getPath()).toAbsolutePath().normalize()))
                .findFirst();
    }

    private boolean canAccessProject(Long projectId) {
        if (projectId == null) {
            return false;
        }
        return projectMemberApplicationService.canAccessProject(projectId);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseArgs(String actionInput) {
        if (actionInput == null || actionInput.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(actionInput.trim(), JSON_MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("工具参数必须是合法 JSON 对象", e);
        }
    }

    private String normalize(String action) {
        return action.toLowerCase().replaceAll("[ _-]", "");
    }
}
