package com.kset.agent.core.extension.output;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.AgentProtocolDefinition;
import com.kset.agent.core.workflow.AgentOutputType;
import org.springframework.stereotype.Component;

@Component
public class JsonAgentOutputConverter implements AgentOutputConverter {
    private final ObjectMapper objectMapper;

    public JsonAgentOutputConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String protocol() {
        return AgentProtocolDefinition.PROTOCOL;
    }

    @Override
    public AgentOutputConversion convert(String raw) {
        if (raw == null || raw.isBlank()) {
            return new AgentOutputConversion(null, null, "模型返回为空，必须返回一个完整 JSON 对象");
        }
        String text = raw.trim();
        AgentOutputConversion extractionError = validateMarkers(text);
        if (extractionError != null) {
            return extractionError;
        }
        // 未带协议标记的模型输出仅作为普通最终回答，绝不参与工具或确认解析。
        if (!text.contains(AgentProtocolDefinition.OUTPUT_START_MARKER)
                && !text.contains(AgentProtocolDefinition.OUTPUT_END_MARKER)) {
            if (isUnmarkedControlJson(text)) {
                return new AgentOutputConversion(null, null,
                        "Agent 控制协议必须使用开始和结束标记包裹");
            }
            return new AgentOutputConversion(
                    objectMapper.createObjectNode()
                            .put("type", AgentOutputType.FINAL_ANSWER.code())
                            .put("answer", text), null, null);
        }
        if (text.contains(AgentProtocolDefinition.OUTPUT_START_MARKER)) {
            int contentStart = text.indexOf(AgentProtocolDefinition.OUTPUT_START_MARKER)
                    + AgentProtocolDefinition.OUTPUT_START_MARKER.length();
            int contentEnd = text.indexOf(AgentProtocolDefinition.OUTPUT_END_MARKER, contentStart);
            text = text.substring(contentStart, contentEnd).trim();
        }
        if (!text.startsWith("{") || !text.endsWith("}")) {
            return new AgentOutputConversion(null, null,
                    "标记区间内必须从 { 开始并以 } 结束，不能包含 Markdown、解释或其他协议外文本");
        }
        try (JsonParser parser = objectMapper.createParser(text)) {
            JsonNode json = objectMapper.readTree(parser);
            JsonToken trailingToken = parser.nextToken();
            if (trailingToken != null) {
                return new AgentOutputConversion(null, null,
                        "模型每轮只能返回一个 JSON 对象，禁止拼接多个 JSON 或附加协议外内容");
            }
            if (json == null || !json.isObject()) {
                return new AgentOutputConversion(null, null, "模型响应必须是一个 JSON 对象");
            }
            if (!json.has("type") && containsWrappedProtocol(json)) {
                return new AgentOutputConversion(null, null,
                        "协议类型必须通过根层 type 字段返回，禁止使用 tool_call、tool_batch、final_answer、confirmation 或 task_plan 作为外层键");
            }
            return new AgentOutputConversion(json, null, null);
        } catch (Exception e) {
            String message = e.getMessage() != null && e.getMessage().length() > 300
                    ? e.getMessage().substring(0, 300) : e.getMessage();
            return new AgentOutputConversion(null, null, "JSON 解析失败: " + message);
        }
    }

    private boolean isUnmarkedControlJson(String text) {
        if (!text.startsWith("{") || !text.endsWith("}")) {
            return false;
        }
        try {
            JsonNode json = objectMapper.readTree(text);
            return json != null && json.isObject()
                    && AgentProtocolDefinition.supports(json.path("type").asText(null));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean containsWrappedProtocol(JsonNode json) {
        return AgentProtocolDefinition.OUTPUT_TYPES.stream().anyMatch(json::has);
    }

    private AgentOutputConversion validateMarkers(String text) {
        int start = text.indexOf(AgentProtocolDefinition.OUTPUT_START_MARKER);
        int end = text.indexOf(AgentProtocolDefinition.OUTPUT_END_MARKER);
        if (start < 0 && end < 0) {
            return null;
        }
        if (start < 0 || end < 0 || end < start) {
            return new AgentOutputConversion(null, null,
                    "Agent JSON 开始和结束标记必须成对且顺序正确");
        }
        int nextStart = text.indexOf(AgentProtocolDefinition.OUTPUT_START_MARKER,
                start + AgentProtocolDefinition.OUTPUT_START_MARKER.length());
        int nextEnd = text.indexOf(AgentProtocolDefinition.OUTPUT_END_MARKER,
                end + AgentProtocolDefinition.OUTPUT_END_MARKER.length());
        if (nextStart >= 0 || nextEnd >= 0) {
            return new AgentOutputConversion(null, null,
                    "模型每轮只能返回一对 Agent JSON 标记");
        }
        return null;
    }
}
