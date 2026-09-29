package com.kset.agent.core.extension.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.AgentProtocolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAgentOutputConverterTest {

    private static final String START = AgentProtocolDefinition.OUTPUT_START_MARKER;
    private static final String END = AgentProtocolDefinition.OUTPUT_END_MARKER;

    private JsonAgentOutputConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JsonAgentOutputConverter(new ObjectMapper());
    }

    @Test
    void protocolIsAgentJsonV1() {
        assertEquals("agent-json-v1", converter.protocol());
    }

    @Test
    void nullOrBlankRawReturnsError() {
        AgentOutputConversion nullResult = converter.convert(null);
        assertFalse(nullResult.valid());
        assertEquals("模型返回为空，必须返回一个完整 JSON 对象", nullResult.error());
        assertNull(nullResult.json());

        AgentOutputConversion blankResult = converter.convert("   ");
        assertFalse(blankResult.valid());
        assertEquals("模型返回为空，必须返回一个完整 JSON 对象", blankResult.error());
    }

    @Test
    void plainTextWithoutMarkersBecomesFinalAnswer() {
        AgentOutputConversion result = converter.convert("这是一段普通回答");
        assertTrue(result.valid());
        assertNull(result.error());
        assertNotNull(result.json());
        assertEquals("final_answer", result.json().get("type").asText());
        assertEquals("这是一段普通回答", result.json().get("answer").asText());
    }

    @Test
    void unmarkedControlJsonIsRejected() {
        AgentOutputConversion result = converter.convert("{\"type\":\"tool_call\",\"toolCall\":{\"toolName\":\"t\"}}");
        assertFalse(result.valid());
        assertEquals("Agent 控制协议必须使用开始和结束标记包裹", result.error());
    }

    @Test
    void unmarkedJsonWithUnknownTypeIsTreatedAsPlainAnswer() {
        String raw = "{\"type\":\"not_a_protocol\"}";
        AgentOutputConversion result = converter.convert(raw);
        assertTrue(result.valid());
        assertEquals("final_answer", result.json().get("type").asText());
        assertEquals(raw, result.json().get("answer").asText());
    }

    @Test
    void missingEndMarkerIsRejected() {
        AgentOutputConversion result = converter.convert(START + "{\"type\":\"final_answer\",\"answer\":\"x\"}");
        assertFalse(result.valid());
        assertEquals("Agent JSON 开始和结束标记必须成对且顺序正确", result.error());
    }

    @Test
    void missingStartMarkerIsRejected() {
        AgentOutputConversion result = converter.convert("{\"type\":\"final_answer\",\"answer\":\"x\"}" + END);
        assertFalse(result.valid());
        assertEquals("Agent JSON 开始和结束标记必须成对且顺序正确", result.error());
    }

    @Test
    void reversedMarkersAreRejected() {
        AgentOutputConversion result = converter.convert(END + "xxx" + START);
        assertFalse(result.valid());
        assertEquals("Agent JSON 开始和结束标记必须成对且顺序正确", result.error());
    }

    @Test
    void duplicateMarkerPairsAreRejected() {
        String json = "{\"type\":\"final_answer\",\"answer\":\"x\"}";
        AgentOutputConversion result = converter.convert(START + json + END + START + json + END);
        assertFalse(result.valid());
        assertEquals("模型每轮只能返回一对 Agent JSON 标记", result.error());
    }

    @Test
    void markerContentMustBeJsonObjectEnvelope() {
        AgentOutputConversion result = converter.convert(START + "not json at all" + END);
        assertFalse(result.valid());
        assertEquals("标记区间内必须从 { 开始并以 } 结束，不能包含 Markdown、解释或其他协议外文本", result.error());
    }

    @Test
    void invalidJsonInsideMarkersReturnsParseError() {
        AgentOutputConversion result = converter.convert(START + "{\"type\":\"final_answer\",}" + END);
        assertFalse(result.valid());
        assertTrue(result.error().startsWith("JSON 解析失败: "));
    }

    @Test
    void concatenatedJsonObjectsAreRejected() {
        AgentOutputConversion result = converter.convert(
                START + "{\"type\":\"tool_batch\"}{\"type\":\"final_answer\",\"answer\":\"x\"}" + END);
        assertFalse(result.valid());
        assertEquals("模型每轮只能返回一个 JSON 对象，禁止拼接多个 JSON 或附加协议外内容", result.error());
    }

    @Test
    void protocolTypeAsOuterKeyIsRejected() {
        AgentOutputConversion result = converter.convert(START + "{\"tool_call\":{\"type\":\"tool_call\"}}" + END);
        assertFalse(result.valid());
        assertEquals("协议类型必须通过根层 type 字段返回，禁止使用 tool_call、tool_batch、final_answer、confirmation 或 task_plan 作为外层键",
                result.error());
    }

    @Test
    void validTaskPlanParses() {
        String raw = START
                + "{\"type\":\"task_plan\",\"plan\":\"执行总纲\",\"tasks\":[{\"taskId\":\"entry\",\"title\":\"定位入口\"}]}"
                + END;
        AgentOutputConversion result = converter.convert(raw);
        assertTrue(result.valid());
        assertEquals("task_plan", result.json().get("type").asText());
        assertEquals("执行总纲", result.json().get("plan").asText());
    }

    @Test
    void validToolCallParses() {
        String raw = START
                + "{\"type\":\"tool_call\",\"toolCall\":{\"taskId\":\"entry\",\"toolName\":\"search\",\"arguments\":{}}}"
                + END;
        AgentOutputConversion result = converter.convert(raw);
        assertTrue(result.valid());
        assertEquals("tool_call", result.json().get("type").asText());
        assertEquals("search", result.json().get("toolCall").get("toolName").asText());
    }

    @Test
    void validToolBatchParses() {
        String raw = START
                + "{\"type\":\"tool_batch\",\"plan\":\"执行摘要\",\"toolCalls\":["
                + "{\"taskId\":\"a\",\"toolName\":\"s1\",\"arguments\":{}},"
                + "{\"taskId\":\"b\",\"toolName\":\"s2\",\"arguments\":{}}]}"
                + END;
        AgentOutputConversion result = converter.convert(raw);
        assertTrue(result.valid());
        assertEquals("tool_batch", result.json().get("type").asText());
        assertEquals(2, result.json().get("toolCalls").size());
    }

    @Test
    void validAnswerChunkParses() {
        String raw = START + "{\"type\":\"answer_chunk\",\"answer\":\"本轮回答片段\"}" + END;
        AgentOutputConversion result = converter.convert(raw);
        assertTrue(result.valid());
        assertEquals("answer_chunk", result.json().get("type").asText());
        assertEquals("本轮回答片段", result.json().get("answer").asText());
    }

    @Test
    void validFinalAnswerParses() {
        String raw = START + "{\"type\":\"final_answer\",\"answer\":\"回答正文\"}" + END;
        AgentOutputConversion result = converter.convert(raw);
        assertTrue(result.valid());
        assertEquals("final_answer", result.json().get("type").asText());
        assertEquals("回答正文", result.json().get("answer").asText());
    }

    @Test
    void validConfirmationParses() {
        String raw = START + "{\"type\":\"confirmation\",\"confirmation\":{\"message\":\"确认内容\"}}" + END;
        AgentOutputConversion result = converter.convert(raw);
        assertTrue(result.valid());
        assertEquals("confirmation", result.json().get("type").asText());
        assertEquals("确认内容", result.json().get("confirmation").get("message").asText());
    }

    @Test
    void textAroundMarkersIsIgnored() {
        String raw = "前缀说明" + START + "{\"type\":\"final_answer\",\"answer\":\"正文\"}" + END + "尾随说明";
        AgentOutputConversion result = converter.convert(raw);
        assertTrue(result.valid());
        assertEquals("final_answer", result.json().get("type").asText());
        assertEquals("正文", result.json().get("answer").asText());
    }
}
