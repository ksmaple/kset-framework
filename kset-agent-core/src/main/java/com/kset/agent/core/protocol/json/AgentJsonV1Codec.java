package com.kset.agent.core.protocol.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.action.AnswerChunkAction;
import com.kset.agent.core.action.ConfirmationAction;
import com.kset.agent.core.action.FinalAnswerAction;
import com.kset.agent.core.action.PlanTask;
import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.action.TaskPlanAction;
import com.kset.agent.core.action.ToolBatchAction;
import com.kset.agent.core.action.ToolCallAction;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolContext;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.protocol.AgentProtocolId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict built-in JSON protocol compatible with the original agent-json-v1 envelope. */
public final class AgentJsonV1Codec implements AgentProtocolCodec {

    public static final AgentProtocolId ID = new AgentProtocolId("agent-json", "v1");
    public static final String START_MARKER = "<<<AGENT_JSON>>>";
    public static final String END_MARKER = "<<<END_AGENT_JSON>>>";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Set<String> STANDARD_TYPES = Set.of(
            StandardActionTypes.TASK_PLAN,
            StandardActionTypes.TOOL_CALL,
            StandardActionTypes.TOOL_BATCH,
            StandardActionTypes.ANSWER_CHUNK,
            StandardActionTypes.FINAL_ANSWER,
            StandardActionTypes.CONFIRMATION);

    private static final String INSTRUCTIONS = """
            Return exactly one decision per turn. Control decisions must be wrapped by
            <<<AGENT_JSON>>> and <<<END_AGENT_JSON>>> and contain one JSON object.
            Supported type values: task_plan, tool_call, tool_batch, answer_chunk,
            final_answer, confirmation. Plain text without markers is treated as final_answer.
            Never place Markdown or another JSON object inside the markers.
            Shapes:
            {"type":"task_plan","plan":"...","tasks":[{"taskId":"...","title":"...","dependsOn":[]}]}
            {"type":"tool_call","toolCall":{"callId":"...","taskId":"...","toolName":"...","arguments":{}}}
            {"type":"tool_batch","plan":"...","toolCalls":[...]}
            {"type":"answer_chunk","answer":"..."}
            {"type":"final_answer","answer":"..."}
            {"type":"confirmation","confirmation":{"confirmationId":"...","message":"...","options":{}}}
            callId is required and must identify one operation within the run. Use a new callId for
            each distinct operation, including later turns, and reuse it only for confirmation,
            retry, reconciliation, or resume of that same operation.
            An optional root metadata object may carry protocol-neutral extension data.
            """;

    private final ObjectMapper objectMapper;

    public AgentJsonV1Codec(ObjectMapper objectMapper) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper").copy();
        this.objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.objectMapper.disable(
                JsonParser.Feature.ALLOW_COMMENTS,
                JsonParser.Feature.ALLOW_YAML_COMMENTS,
                JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES,
                JsonParser.Feature.ALLOW_SINGLE_QUOTES,
                JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS,
                JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
                JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS,
                JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS,
                JsonParser.Feature.ALLOW_MISSING_VALUES,
                JsonParser.Feature.ALLOW_TRAILING_COMMA);
    }

    @Override
    public AgentProtocolId protocolId() {
        return ID;
    }

    @Override
    public ModelRequest prepare(ModelRequest request, AgentProtocolContext context) {
        String system = request.systemPrompt().isBlank()
                ? INSTRUCTIONS : request.systemPrompt() + "\n\n" + INSTRUCTIONS;
        return request.withSystemPrompt(system);
    }

    @Override
    public AgentDecision decode(ModelResponse response, AgentProtocolContext context) {
        String raw = response == null ? "" : response.text().trim();
        if (raw.isBlank()) {
            throw error(AgentJsonV1ErrorCode.EMPTY_RESPONSE, "model response is empty");
        }
        if (!raw.contains(START_MARKER) && !raw.contains(END_MARKER)) {
            if (looksLikeControlJson(raw)) {
                throw error(AgentJsonV1ErrorCode.MISSING_MARKERS,
                        "control JSON must be wrapped by protocol markers");
            }
            return AgentDecision.of(new FinalAnswerAction(raw));
        }
        String jsonText = extractEnvelope(raw);
        JsonNode root = parseObject(jsonText);
        String type = requiredText(root, "type");
        validateRootFields(root, type);
        try {
            Map<String, Object> metadata = rootMetadata(root);
            return new AgentDecision(List.of(switch (type) {
                case StandardActionTypes.TASK_PLAN -> taskPlan(root);
                case StandardActionTypes.TOOL_CALL -> toolCall(root.path("toolCall"), "toolCall");
                case StandardActionTypes.TOOL_BATCH -> toolBatch(root);
                case StandardActionTypes.ANSWER_CHUNK -> new AnswerChunkAction(requiredText(root, "answer"));
                case StandardActionTypes.FINAL_ANSWER -> new FinalAnswerAction(requiredText(root, "answer"));
                case StandardActionTypes.CONFIRMATION -> confirmation(root.path("confirmation"));
                default -> throw error(AgentJsonV1ErrorCode.UNSUPPORTED_ACTION,
                        "unsupported action type: " + type);
            }), metadata);
        } catch (AgentProtocolException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new AgentProtocolException(AgentJsonV1ErrorCode.INVALID_SCHEMA.name(),
                    "protocol action fields are invalid", exception);
        }
    }

    private String extractEnvelope(String raw) {
        int start = raw.indexOf(START_MARKER);
        int end = raw.indexOf(END_MARKER);
        if (start < 0 || end < 0 || end < start) {
            throw error(AgentJsonV1ErrorCode.INVALID_MARKERS,
                    "protocol markers must be paired and ordered");
        }
        if (raw.indexOf(START_MARKER, start + START_MARKER.length()) >= 0
                || raw.indexOf(END_MARKER, end + END_MARKER.length()) >= 0) {
            throw error(AgentJsonV1ErrorCode.MULTIPLE_ENVELOPES,
                    "only one protocol envelope is allowed");
        }
        String before = raw.substring(0, start).trim();
        String after = raw.substring(end + END_MARKER.length()).trim();
        if (!before.isEmpty() || !after.isEmpty()) {
            throw error(AgentJsonV1ErrorCode.OUTSIDE_CONTENT,
                    "content outside the protocol envelope is not allowed");
        }
        return raw.substring(start + START_MARKER.length(), end).trim();
    }

    private JsonNode parseObject(String text) {
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw error(AgentJsonV1ErrorCode.INVALID_JSON_BOUNDARY,
                    "protocol payload must be one JSON object");
        }
        try (JsonParser parser = objectMapper.createParser(text)) {
            JsonNode root = objectMapper.readTree(parser);
            JsonToken trailing = parser.nextToken();
            if (root == null || !root.isObject() || trailing != null) {
                throw error(AgentJsonV1ErrorCode.INVALID_JSON,
                        "protocol payload must contain exactly one JSON object");
            }
            return root;
        } catch (AgentProtocolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentProtocolException(AgentJsonV1ErrorCode.INVALID_JSON.name(),
                    "protocol JSON cannot be parsed", exception);
        }
    }

    private TaskPlanAction taskPlan(JsonNode root) {
        String summary = requiredText(root, "plan");
        JsonNode values = root.path("tasks");
        if (!values.isArray() || values.isEmpty()) {
            throw error(AgentJsonV1ErrorCode.INVALID_PLAN,
                    "task_plan requires at least one task");
        }
        List<PlanTask> tasks = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            JsonNode value = values.get(index);
            if (!value.isObject()) {
                throw error(AgentJsonV1ErrorCode.INVALID_PLAN,
                        "tasks[" + index + "] must be an object");
            }
            requireOnlyFields(value, "tasks[" + index + "]", Set.of("taskId", "title", "dependsOn"));
            List<String> dependencies = new ArrayList<>();
            JsonNode dependencyValues = value.path("dependsOn");
            if (!dependencyValues.isMissingNode() && !dependencyValues.isArray()) {
                throw error(AgentJsonV1ErrorCode.INVALID_PLAN,
                        "tasks[" + index + "].dependsOn must be an array");
            }
            for (int dependencyIndex = 0; dependencyIndex < dependencyValues.size(); dependencyIndex++) {
                JsonNode dependency = dependencyValues.get(dependencyIndex);
                if (!dependency.isTextual() || dependency.asText().isBlank()) {
                    throw error(AgentJsonV1ErrorCode.INVALID_PLAN,
                            "task dependency ids must be non-blank strings");
                }
                dependencies.add(dependency.asText());
            }
            tasks.add(new PlanTask(requiredText(value, "taskId"), requiredText(value, "title"), dependencies));
        }
        return new TaskPlanAction(summary, tasks);
    }

    private ToolBatchAction toolBatch(JsonNode root) {
        JsonNode values = root.path("toolCalls");
        if (!values.isArray() || values.size() < 2) {
            throw error(AgentJsonV1ErrorCode.INVALID_TOOL_BATCH,
                    "tool_batch requires at least two tool calls");
        }
        String summary = requiredText(root, "plan");
        List<ToolCallAction> toolCalls = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            toolCalls.add(toolCall(values.get(index), "toolCalls[" + index + "]"));
        }
        return new ToolBatchAction(summary, toolCalls);
    }

    private ToolCallAction toolCall(JsonNode value, String path) {
        if (!value.isObject()) {
            throw error(AgentJsonV1ErrorCode.INVALID_TOOL_CALL, path + " must be an object");
        }
        requireOnlyFields(value, path, Set.of("callId", "taskId", "toolName", "arguments"));
        String toolName = requiredText(value, "toolName");
        JsonNode arguments = value.path("arguments");
        if (!arguments.isObject()) {
            throw error(AgentJsonV1ErrorCode.INVALID_TOOL_ARGUMENTS,
                    path + ".arguments must be an object");
        }
        return new ToolCallAction(requiredText(value, "callId"), value.path("taskId").asText(null),
                toolName, objectMapper.convertValue(arguments, MAP_TYPE));
    }

    private ConfirmationAction confirmation(JsonNode value) {
        if (!value.isObject()) {
            throw error(AgentJsonV1ErrorCode.INVALID_CONFIRMATION,
                    "confirmation must be an object");
        }
        requireOnlyFields(value, "confirmation",
                Set.of("confirmationId", "message", "options"));
        JsonNode optionValues = value.path("options");
        if (!optionValues.isMissingNode() && !optionValues.isObject()) {
            throw error(AgentJsonV1ErrorCode.INVALID_CONFIRMATION,
                    "confirmation.options must be an object");
        }
        Map<String, Object> options = optionValues.isMissingNode()
                ? Map.of() : objectMapper.convertValue(optionValues, MAP_TYPE);
        return new ConfirmationAction(requiredText(value, "confirmationId"),
                requiredText(value, "message"), options);
    }

    private Map<String, Object> rootMetadata(JsonNode root) {
        JsonNode metadata = root.path("metadata");
        if (metadata.isMissingNode()) {
            return Map.of();
        }
        if (!metadata.isObject()) {
            throw error(AgentJsonV1ErrorCode.INVALID_METADATA, "metadata must be an object");
        }
        return objectMapper.convertValue(metadata, MAP_TYPE);
    }

    private void validateRootFields(JsonNode root, String type) {
        Set<String> allowed = switch (type) {
            case StandardActionTypes.TASK_PLAN -> Set.of("type", "plan", "tasks", "metadata");
            case StandardActionTypes.TOOL_CALL -> Set.of("type", "toolCall", "metadata");
            case StandardActionTypes.TOOL_BATCH -> Set.of("type", "plan", "toolCalls", "metadata");
            case StandardActionTypes.ANSWER_CHUNK, StandardActionTypes.FINAL_ANSWER ->
                    Set.of("type", "answer", "metadata");
            case StandardActionTypes.CONFIRMATION -> Set.of("type", "confirmation", "metadata");
            default -> throw error(AgentJsonV1ErrorCode.UNSUPPORTED_ACTION,
                    "unsupported action type: " + type);
        };
        requireOnlyFields(root, "root", allowed);
    }

    private void requireOnlyFields(JsonNode value, String path, Set<String> allowed) {
        value.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw error(AgentJsonV1ErrorCode.UNKNOWN_FIELD,
                        path + " contains unsupported field: " + field);
            }
            if (value.path(field).isNull()) {
                throw error(AgentJsonV1ErrorCode.INVALID_SCHEMA,
                        path + " contains null field: " + field);
            }
        });
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw error(AgentJsonV1ErrorCode.MISSING_FIELD,
                    field + " must be a non-blank string");
        }
        return value.asText();
    }

    private boolean looksLikeControlJson(String text) {
        if (!text.startsWith("{") || !text.endsWith("}")) {
            return false;
        }
        try (JsonParser parser = objectMapper.createParser(text)) {
            parser.disable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.FIELD_NAME && "type".equals(parser.currentName())) {
                    JsonToken value = parser.nextToken();
                    if (value == JsonToken.VALUE_STRING
                            && STANDARD_TYPES.contains(parser.getValueAsString())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private AgentProtocolException error(
            AgentJsonV1ErrorCode errorCode, String errorMessage) {
        return new AgentProtocolException(errorCode.name(), errorMessage);
    }
}
