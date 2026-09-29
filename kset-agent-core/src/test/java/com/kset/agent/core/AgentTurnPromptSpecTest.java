package com.kset.agent.core;

import com.kset.agent.core.workflow.AgentAllowedAction;
import com.kset.agent.core.workflow.AgentExecutionStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTurnPromptSpecTest {

    @Test
    void planningStageWhenNoPlanAndNoTasks() {
        AgentTurnPromptSpec spec = AgentTurnPromptSpec.resolve(false, false, false, false, false);
        assertEquals(AgentExecutionStage.PLANNING, spec.stage());
        assertEquals(List.of(AgentAllowedAction.TASK_PLAN, AgentAllowedAction.CONFIRMATION,
                AgentAllowedAction.FINAL_ANSWER), spec.allowedActions());
        assertEquals(List.of("task_plan", "confirmation", "final_answer"), spec.actionCodes());
        assertTrue(spec.allows(AgentAllowedAction.TASK_PLAN));
        assertFalse(spec.allows(AgentAllowedAction.TOOL_CALL));
    }

    @Test
    void readyToActStageWhenPlanCreatedWithoutObservations() {
        AgentTurnPromptSpec spec = AgentTurnPromptSpec.resolve(true, true, false, false, false);
        assertEquals(AgentExecutionStage.READY_TO_ACT, spec.stage());
        assertEquals(List.of(AgentAllowedAction.TOOL_CALL, AgentAllowedAction.TOOL_BATCH,
                AgentAllowedAction.CONFIRMATION, AgentAllowedAction.FINAL_ANSWER), spec.allowedActions());
        assertTrue(spec.allows(AgentAllowedAction.TOOL_BATCH));
        assertFalse(spec.allows(AgentAllowedAction.ANSWER_CHUNK));
    }

    @Test
    void evaluatingStageWhenObservationsExist() {
        AgentTurnPromptSpec spec = AgentTurnPromptSpec.resolve(true, true, false, true, false);
        assertEquals(AgentExecutionStage.EVALUATING, spec.stage());
        assertEquals(List.of(AgentAllowedAction.TOOL_CALL, AgentAllowedAction.TOOL_BATCH,
                AgentAllowedAction.CONFIRMATION, AgentAllowedAction.FINAL_ANSWER), spec.allowedActions());
    }

    @Test
    void answeringStageWhenAnswerChunksExist() {
        AgentTurnPromptSpec spec = AgentTurnPromptSpec.resolve(true, true, true, true, false);
        assertEquals(AgentExecutionStage.ANSWERING, spec.stage());
        assertEquals(List.of(AgentAllowedAction.ANSWER_CHUNK, AgentAllowedAction.FINAL_ANSWER),
                spec.allowedActions());
        assertEquals(List.of("answer_chunk", "final_answer"), spec.actionCodes());
    }

    @Test
    void answeringStageWhenReadyToAnswer() {
        AgentTurnPromptSpec spec = AgentTurnPromptSpec.resolve(false, false, false, false, true);
        assertEquals(AgentExecutionStage.ANSWERING, spec.stage());
        assertEquals(List.of(AgentAllowedAction.ANSWER_CHUNK, AgentAllowedAction.FINAL_ANSWER),
                spec.allowedActions());
    }

    @Test
    void plannedTasksWithoutCreatedPlanYieldsReadyToActStageWithPlanActions() {
        // 真实行为：stage 依据 !taskPlanCreated && !hasPlannedTasks 判断，允许动作只看 taskPlanCreated。
        AgentTurnPromptSpec spec = AgentTurnPromptSpec.resolve(false, true, false, false, false);
        assertEquals(AgentExecutionStage.READY_TO_ACT, spec.stage());
        assertEquals(List.of(AgentAllowedAction.TASK_PLAN, AgentAllowedAction.CONFIRMATION,
                AgentAllowedAction.FINAL_ANSWER), spec.allowedActions());
    }
}
