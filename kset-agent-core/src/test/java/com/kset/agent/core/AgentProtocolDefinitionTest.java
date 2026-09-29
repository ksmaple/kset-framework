package com.kset.agent.core;

import com.kset.agent.core.workflow.AgentExecutionStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProtocolDefinitionTest {

    @Test
    void supportsAllSixProtocolTypes() {
        assertTrue(AgentProtocolDefinition.supports("task_plan"));
        assertTrue(AgentProtocolDefinition.supports("tool_call"));
        assertTrue(AgentProtocolDefinition.supports("tool_batch"));
        assertTrue(AgentProtocolDefinition.supports("answer_chunk"));
        assertTrue(AgentProtocolDefinition.supports("final_answer"));
        assertTrue(AgentProtocolDefinition.supports("confirmation"));
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertFalse(AgentProtocolDefinition.supports(null));
        assertFalse(AgentProtocolDefinition.supports(""));
        assertFalse(AgentProtocolDefinition.supports("unknown"));
        // supports 基于精确匹配，不做大小写归一
        assertFalse(AgentProtocolDefinition.supports("TOOL_CALL"));
        assertFalse(AgentProtocolDefinition.supports(" tool_call"));
    }

    @Test
    void systemPromptRejectsInternalStages() {
        for (AgentExecutionStage stage : List.of(AgentExecutionStage.PERCEIVING,
                AgentExecutionStage.EXECUTING, AgentExecutionStage.PERSISTING_MEMORY)) {
            AgentTurnPromptSpec spec = new AgentTurnPromptSpec(stage, List.of());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> AgentProtocolDefinition.systemPrompt(spec, 1000));
            assertEquals("该内部阶段不允许直接发起模型调用: " + stage, ex.getMessage());
        }
    }

    @Test
    void systemPromptContainsCorePromptForCallableStages() {
        AgentTurnPromptSpec planning = AgentTurnPromptSpec.resolve(false, false, false, false, false);
        String planningPrompt = AgentProtocolDefinition.systemPrompt(planning, 1000);
        assertTrue(planningPrompt.contains("结构化 Agent 编排模型"));
        assertTrue(planningPrompt.contains("本轮阶段 planning"));

        AgentTurnPromptSpec acting = AgentTurnPromptSpec.resolve(true, true, false, false, false);
        String actingPrompt = AgentProtocolDefinition.systemPrompt(acting, 1000);
        assertTrue(actingPrompt.contains("本轮阶段 acting"));

        AgentTurnPromptSpec answering = AgentTurnPromptSpec.resolve(true, true, true, false, false);
        String answeringPrompt = AgentProtocolDefinition.systemPrompt(answering, 1000);
        assertTrue(answeringPrompt.contains("本轮阶段 answering"));

        AgentTurnPromptSpec evaluating = AgentTurnPromptSpec.resolve(true, true, false, true, false);
        String evaluatingPrompt = AgentProtocolDefinition.systemPrompt(evaluating, 1000);
        assertTrue(evaluatingPrompt.contains("本轮阶段 acting"));
        assertTrue(evaluatingPrompt.contains("planCompleted=false 时禁止 final_answer"));
    }

    @Test
    void protocolConstantsMatchMarkers() {
        assertEquals("<<<AGENT_JSON>>>", AgentProtocolDefinition.OUTPUT_START_MARKER);
        assertEquals("<<<END_AGENT_JSON>>>", AgentProtocolDefinition.OUTPUT_END_MARKER);
        assertEquals("agent-json-v1", AgentProtocolDefinition.PROTOCOL);
        assertEquals(AgentProtocolDefinition.OUTPUT_START_MARKER.length()
                + AgentProtocolDefinition.OUTPUT_END_MARKER.length(), AgentProtocolDefinition.markerChars());
    }
}
