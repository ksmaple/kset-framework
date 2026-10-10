package com.kset.agent.core.strategy;

import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.action.ExtensionAction;
import com.kset.agent.core.action.PlanTask;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.action.ToolBatchAction;
import com.kset.agent.core.action.ToolCallAction;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.loop.AgentRunState;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.tool.AgentToolDescriptor;
import com.kset.agent.core.tool.AgentToolRegistry;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Default ReAct strategy. Its phases are deliberately kept outside the loop kernel. */
public final class ReactReasoningStrategy implements AgentReasoningStrategy {

    public static final String PHASE_ATTRIBUTE = "react.phase";

    private final AgentToolRegistry toolRegistry;

    public ReactReasoningStrategy(AgentToolRegistry toolRegistry) {
        this.toolRegistry = java.util.Objects.requireNonNull(toolRegistry, "toolRegistry");
    }

    @Override
    public String strategyId() {
        return "react";
    }

    @Override
    public AgentTurn nextTurn(AgentRequest request, AgentRunState state) {
        ReactPhase phase = resolvePhase(state);
        Map<String, Object> visibleState = modelVisibleState(state);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("strategy", strategyId());
        attributes.put("phase", phase.name().toLowerCase(java.util.Locale.ROOT));
        attributes.put("tools", toolRegistry.list());
        attributes.put("observations", state.observations());
        attributes.put("state", visibleState);
        attributes.put("approvedConfirmationIds", approvedIds(request,
                AgentRequest.APPROVED_CONFIRMATION_IDS));
        attributes.put("approvedToolCallIds", approvedIds(request,
                AgentRequest.APPROVED_TOOL_CALL_IDS));
        return new AgentTurn(request.protocolId(), new ModelRequest(
                systemPrompt(phase),
                userPrompt(request, state, toolRegistry.list(), visibleState),
                request.options().maxOutputTokens(), attributes));
    }

    @Override
    public void validate(AgentRequest request, AgentRunState state, AgentDecision decision) {
        ReactPhase phase = resolvePhase(state);
        Set<String> allowed = allowedTypes(phase);
        for (AgentAction action : decision.actions()) {
            boolean extensionAllowed = action instanceof ExtensionAction
                    && (phase == ReactPhase.ACTING || phase == ReactPhase.EVALUATING);
            if (!allowed.contains(action.actionType()) && !extensionAllowed) {
                throw new AgentProtocolException("ACTION_NOT_ALLOWED",
                        "action " + action.actionType() + " is not allowed during " + phase);
            }
            validatePlannedTaskReference(state, action);
        }
    }

    @Override
    public AgentRunState afterTurn(AgentRequest request, AgentRunState state,
                                   AgentDecision decision,
                                   List<com.kset.agent.core.action.AgentActionResult> results,
                                   java.time.Instant now) {
        return state.withAttributes(Map.of(PHASE_ATTRIBUTE,
                resolvePhase(state).name().toLowerCase(java.util.Locale.ROOT)), now);
    }

    private ReactPhase resolvePhase(AgentRunState state) {
        if (!strings(state.attributes().get(StandardActionHandlers.ANSWER_CHUNKS)).isEmpty()) {
            return ReactPhase.ANSWERING;
        }
        if (!Boolean.TRUE.equals(state.attributes().get(StandardActionHandlers.PLAN_CREATED))) {
            return ReactPhase.PLANNING;
        }
        boolean hasToolObservation = state.observations().stream()
                .anyMatch(observation -> StandardActionTypes.TOOL_CALL.equals(observation.actionType())
                        || StandardActionTypes.TOOL_BATCH.equals(observation.actionType()));
        return hasToolObservation ? ReactPhase.EVALUATING : ReactPhase.ACTING;
    }

    private Set<String> allowedTypes(ReactPhase phase) {
        if (phase == ReactPhase.ANSWERING) {
            return Set.of(StandardActionTypes.ANSWER_CHUNK, StandardActionTypes.FINAL_ANSWER);
        }
        if (phase == ReactPhase.PLANNING) {
            return Set.of(StandardActionTypes.TASK_PLAN, StandardActionTypes.CONFIRMATION,
                    StandardActionTypes.FINAL_ANSWER);
        }
        return Set.of(StandardActionTypes.TOOL_CALL, StandardActionTypes.TOOL_BATCH,
                StandardActionTypes.CONFIRMATION, StandardActionTypes.FINAL_ANSWER);
    }

    private void validatePlannedTaskReference(AgentRunState state, AgentAction action) {
        if (!Boolean.TRUE.equals(state.attributes().get(StandardActionHandlers.PLAN_CREATED))) {
            return;
        }
        Set<String> taskIds = plannedTaskIds(state.attributes().get(StandardActionHandlers.PLAN_TASKS));
        if (action instanceof ToolCallAction call && !taskIds.contains(call.taskId())) {
            throw new AgentProtocolException("UNKNOWN_PLAN_TASK",
                    "tool call must reference an existing plan task");
        }
        if (action instanceof ToolBatchAction batch
                && batch.toolCalls().stream()
                .anyMatch(toolCall -> !taskIds.contains(toolCall.taskId()))) {
            throw new AgentProtocolException("UNKNOWN_PLAN_TASK",
                    "every tool batch call must reference an existing plan task");
        }
    }

    private Set<String> plannedTaskIds(Object value) {
        if (!(value instanceof Collection<?> tasks)) {
            return Set.of();
        }
        Set<String> taskIds = new HashSet<>();
        for (Object task : tasks) {
            if (task instanceof PlanTask planTask) {
                taskIds.add(planTask.taskId());
            } else if (task instanceof Map<?, ?> taskState) {
                Object taskId = taskState.get("taskId");
                if (taskId != null && !String.valueOf(taskId).isBlank()) {
                    taskIds.add(String.valueOf(taskId));
                }
            }
        }
        return Set.copyOf(taskIds);
    }

    private String systemPrompt(ReactPhase phase) {
        return switch (phase) {
            case PLANNING -> "Analyze the task. Create a concise plan when tools are needed, or answer directly.";
            case ACTING -> "Select the smallest useful tool action from the available tools.";
            case EVALUATING -> "Evaluate observations against the task. Continue only when information is missing.";
            case ANSWERING -> "Finish the answer. Do not call tools after answer chunks have started.";
        };
    }

    private String userPrompt(
            AgentRequest request, AgentRunState state,
            List<AgentToolDescriptor> toolDescriptors, Map<String, Object> visibleState) {
        StringBuilder prompt = new StringBuilder("Task:\n").append(request.task());
        if (!toolDescriptors.isEmpty()) {
            prompt.append("\n\nAvailable tools:");
            toolDescriptors.forEach(tool ->
                    prompt.append("\n- ").append(tool.toolName()).append(": ")
                    .append(tool.description()).append(" schema=").append(tool.inputSchema()));
        }
        if (!state.observations().isEmpty()) {
            prompt.append("\n\nObservations:");
            state.observations().forEach(observation -> prompt.append("\n- ")
                    .append(observation.actionType()).append(" success=")
                    .append(observation.success()).append(" callId=")
                    .append(observation.attributes().get(AgentObservation.TOOL_CALL_ID))
                    .append(" errorCode=")
                    .append(observation.errorCode()).append(" errorMessage=")
                    .append(observation.errorMessage()).append(" output=")
                    .append(observation.output()));
        }
        if (!visibleState.isEmpty()) {
            prompt.append("\n\nRuntime state:\n").append(visibleState);
        }
        List<String> approvedConfirmationIds = approvedIds(request,
                AgentRequest.APPROVED_CONFIRMATION_IDS);
        if (!approvedConfirmationIds.isEmpty()) {
            prompt.append("\n\nApproved confirmation IDs:\n")
                    .append(approvedConfirmationIds);
        }
        List<String> approvedToolCallIds = approvedIds(request,
                AgentRequest.APPROVED_TOOL_CALL_IDS);
        if (!approvedToolCallIds.isEmpty()) {
            prompt.append("\n\nApproved tool call IDs:\n").append(approvedToolCallIds);
        }
        Object protocolError = state.attributes().get("agent.lastProtocolError");
        if (protocolError != null) {
            prompt.append("\n\nCorrect the previous protocol error: ").append(protocolError);
        }
        return prompt.toString();
    }

    private Map<String, Object> modelVisibleState(AgentRunState state) {
        Map<String, Object> visible = new LinkedHashMap<>(state.attributes());
        visible.remove(AgentRunState.TOOL_OPERATIONS_ATTRIBUTE);
        visible.remove(StandardActionHandlers.APPROVED_TOOL_OPERATIONS);
        return Map.copyOf(visible);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private List<String> approvedIds(AgentRequest request, String attributeKey) {
        Object value = request.attributes().get(attributeKey);
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .filter(id -> !id.isBlank())
                    .toList();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return List.of(String.valueOf(value));
    }
}
