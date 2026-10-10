package com.kset.agent.core.protocol.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AnswerChunkAction;
import com.kset.agent.core.action.ConfirmationAction;
import com.kset.agent.core.action.FinalAnswerAction;
import com.kset.agent.core.action.TaskPlanAction;
import com.kset.agent.core.action.ToolBatchAction;
import com.kset.agent.core.action.ToolCallAction;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.loop.AgentLoopKernel;
import com.kset.agent.core.loop.AgentLoopOptions;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentCancellation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentJsonV1CodecTest {

    private final AgentJsonV1Codec codec = new AgentJsonV1Codec(new ObjectMapper());

    @Test
    void mapsAllSixBuiltInActionShapesAndMetadata() {
        AgentDecision plan = decode("""
                {"type":"task_plan","plan":"inspect","tasks":[
                  {"taskId":"inspect","title":"Inspect","dependsOn":[]}
                ],"metadata":{"source":"test"}}
                """);
        AgentDecision call = decode("""
                {"type":"tool_call","toolCall":{
                  "callId":"call-1","taskId":"inspect","toolName":"search",
                  "arguments":{"query":"kernel"}
                }}
                """);
        AgentDecision batch = decode("""
                {"type":"tool_batch","plan":"parallel","toolCalls":[
                  {"callId":"call-2","taskId":"inspect","toolName":"search","arguments":{}},
                  {"callId":"call-3","taskId":"inspect","toolName":"search","arguments":{}}
                ]}
                """);
        AgentDecision chunk = decode("{" +
                "\"type\":\"answer_chunk\",\"answer\":\"part\"}");
        AgentDecision answer = decode("{" +
                "\"type\":\"final_answer\",\"answer\":\"done\"}");
        AgentDecision confirmation = decode("""
                {"type":"confirmation","confirmation":{
                  "confirmationId":"confirm-1","message":"Continue?","options":{"level":"high"}
                }}
                """);

        assertThat(plan.actions().getFirst()).isInstanceOf(TaskPlanAction.class);
        assertThat(plan.metadata()).containsEntry("source", "test");
        assertThat(call.actions().getFirst()).isInstanceOfSatisfying(
                ToolCallAction.class, action -> {
                    assertThat(action.callId()).isEqualTo("call-1");
                    assertThat(action.arguments()).containsEntry("query", "kernel");
                });
        assertThat(batch.actions().getFirst()).isInstanceOfSatisfying(
                ToolBatchAction.class,
                action -> assertThat(action.toolCalls()).hasSize(2));
        assertThat(chunk.actions().getFirst()).isInstanceOfSatisfying(
                AnswerChunkAction.class,
                action -> assertThat(action.text()).isEqualTo("part"));
        assertThat(answer.actions().getFirst()).isInstanceOfSatisfying(
                FinalAnswerAction.class,
                action -> assertThat(action.answer()).isEqualTo("done"));
        assertThat(confirmation.actions().getFirst()).isInstanceOfSatisfying(
                ConfirmationAction.class,
                action -> assertThat(action.options()).containsEntry("level", "high"));
    }

    @Test
    void treatsPlainTextAsAnswerButRejectsUnwrappedControlJson() {
        AgentDecision plain = codec.decode(ModelResponse.text("plain answer"), null);

        assertThat(plain.actions().getFirst()).isInstanceOfSatisfying(
                FinalAnswerAction.class,
                action -> assertThat(action.answer()).isEqualTo("plain answer"));
        assertProtocolError(
                "{\"type\":\"final_answer\",\"answer\":\"hidden control\"}",
                AgentJsonV1ErrorCode.MISSING_MARKERS);
    }

    @Test
    void reportsStableErrorsForInvalidEnvelopeFieldsAndMetadata() {
        assertProtocolError(
                "prefix " + envelope("{\"type\":\"final_answer\",\"answer\":\"done\"}"),
                AgentJsonV1ErrorCode.OUTSIDE_CONTENT);
        assertProtocolError(envelope(
                "{\"type\":\"final_answer\",\"answer\":\"done\",\"extra\":true}"),
                AgentJsonV1ErrorCode.UNKNOWN_FIELD);
        assertProtocolError(envelope(
                "{\"type\":\"final_answer\",\"answer\":\"done\",\"metadata\":[]}"),
                AgentJsonV1ErrorCode.INVALID_METADATA);
        assertProtocolError(envelope(
                "{\"type\":\"final_answer\",\"answer\":\"one\",\"answer\":\"two\"}"),
                AgentJsonV1ErrorCode.INVALID_JSON);
    }

    @Test
    void preservesBuiltInProtocolAndAllowsDistinctCustomProtocol() {
        AgentProtocolId customId = new AgentProtocolId("custom", "v2");
        AgentProtocolCodec custom = new AgentProtocolCodec() {
            @Override
            public AgentProtocolId protocolId() {
                return customId;
            }

            @Override
            public AgentDecision decode(
                    ModelResponse response,
                    com.kset.agent.core.protocol.AgentProtocolContext context) {
                return AgentDecision.of(new FinalAnswerAction("custom answer"));
            }
        };
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) ->
                        ModelResponse.text("custom payload"))
                .protocol(custom)
                .build();

        assertThat(kernel.supportedProtocolIds())
                .containsExactly(AgentJsonV1Codec.ID, customId);
        AgentResult result = kernel.run(new AgentRequest(
                "custom-run", "custom task", customId, AgentLoopOptions.defaults(),
                Map.of(), AgentCancellation.none()));
        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("custom answer");

        assertThatThrownBy(() -> AgentLoopKernel.builder((request, context) ->
                        ModelResponse.text("unused"))
                .protocol(new AgentJsonV1Codec(new ObjectMapper()))
                .build())
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_CONFIGURATION));
    }

    private AgentDecision decode(String json) {
        return codec.decode(ModelResponse.text(envelope(json)), null);
    }

    private void assertProtocolError(String response, AgentJsonV1ErrorCode expected) {
        assertThatThrownBy(() -> codec.decode(ModelResponse.text(response), null))
                .isInstanceOfSatisfying(AgentProtocolException.class,
                        error -> assertThat(error.protocolErrorCode()).isEqualTo(expected.name()));
    }

    private static String envelope(String json) {
        return AgentJsonV1Codec.START_MARKER + "\n" + json + "\n"
                + AgentJsonV1Codec.END_MARKER;
    }
}
