package com.kset.agent.core.protocol.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.action.ExtensionAction;
import com.kset.agent.core.action.ToolFlowPlan;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolCapacityException;
import com.kset.agent.core.protocol.AgentProtocolContext;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.protocol.AgentProtocolId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opt-in JSON protocol that adds a read-only dependency graph to agent-json:v1. */
public final class AgentJsonFlowV1Codec implements AgentProtocolCodec {

    public static final AgentProtocolId ID = new AgentProtocolId("agent-json-flow", "v1");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String FLOW_INSTRUCTIONS = """
            Optional tool_flow for read-only tools only: {"type":"tool_flow","plan":"...",
            "toolCalls":[{"callId":"...","taskId":"...","toolName":"...",
            "arguments":{},"dependsOn":[]},...]}. Independent calls run in parallel.
            A later argument may be {"$toolResult":{"callId":"earlier-call-id","path":"/field"}};
            the referenced callId must appear in that call's dependsOn. The path is a JSON Pointer;
            use "" for the whole result. Failed dependencies prevent dependent calls from running.
            Do not include writes, sends, creations or tools requiring confirmation in tool_flow.
            """;

    private final ObjectMapper objectMapper;
    private final AgentJsonV1Codec standard;

    public AgentJsonFlowV1Codec(ObjectMapper objectMapper) {
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
        standard = new AgentJsonV1Codec(this.objectMapper);
    }

    @Override
    public AgentProtocolId protocolId() {
        return ID;
    }

    @Override
    public ModelRequest prepare(ModelRequest request, AgentProtocolContext context) {
        ModelRequest prepared = standard.prepare(request, context);
        Object allowed = request.attributes().get(
                ModelRequest.ALLOWED_STANDARD_ACTION_TYPES_ATTRIBUTE);
        if (allowed instanceof List<?> names && !names.contains("tool_call")) {
            return prepared;
        }
        return prepared.withSystemPrompt(prepared.systemPrompt() + "\n" + FLOW_INSTRUCTIONS);
    }

    @Override
    public AgentDecision decode(ModelResponse response, AgentProtocolContext context) {
        String raw = response == null ? "" : response.text().trim();
        String start = AgentJsonV1Codec.START_MARKER;
        String end = AgentJsonV1Codec.END_MARKER;
        if (!raw.startsWith(start) || !raw.endsWith(end)
                || raw.indexOf(start, start.length()) >= 0
                || raw.indexOf(end) != raw.length() - end.length()) {
            return standard.decode(response, context);
        }
        JsonNode root;
        try (JsonParser parser = objectMapper.createParser(
                raw.substring(start.length(), raw.length() - end.length()))) {
            root = objectMapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) {
                return standard.decode(response, context);
            }
        } catch (Exception error) {
            return standard.decode(response, context);
        }
        if (!ToolFlowPlan.ACTION_TYPE.equals(root.path("type").asText())) {
            return standard.decode(response, context);
        }
        Set<String> allowed = Set.of("type", "plan", "toolCalls", "metadata");
        var fields = root.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw invalid("tool_flow contains unsupported root fields");
            }
        }
        if (!root.path("plan").isTextual() || !root.path("toolCalls").isArray()) {
            throw invalid("tool_flow plan or toolCalls is invalid");
        }
        JsonNode metadataNode = root.path("metadata");
        if (!metadataNode.isMissingNode() && !metadataNode.isObject()) {
            throw invalid("tool_flow metadata must be an object");
        }
        if (root.path("toolCalls").size()
                > context.request().options().maxToolCallsPerBatch()) {
            throw new AgentProtocolCapacityException("tool flow size limit exceeded");
        }
        Map<String, Object> payload = Map.of(
                "plan", root.path("plan").asText(),
                "toolCalls", objectMapper.convertValue(root.path("toolCalls"), List.class));
        try {
            ToolFlowPlan.parse(payload);
        } catch (IllegalArgumentException error) {
            throw new AgentProtocolException("INVALID_TOOL_FLOW", error.getMessage(), error);
        }
        try {
            Map<String, Object> metadata = metadataNode.isMissingNode()
                    ? Map.of() : objectMapper.convertValue(metadataNode, MAP_TYPE);
            return new AgentDecision(
                    List.of(new ExtensionAction(ToolFlowPlan.ACTION_TYPE, payload)), metadata);
        } catch (IllegalArgumentException error) {
            throw new AgentProtocolException("INVALID_TOOL_FLOW", "tool_flow metadata is invalid", error);
        }
    }

    private static AgentProtocolException invalid(String message) {
        return new AgentProtocolException("INVALID_TOOL_FLOW", message);
    }
}
