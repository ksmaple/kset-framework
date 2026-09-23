package com.kset.agent.core;

import com.kset.agent.workflow.AgentAllowedAction;
import com.kset.agent.workflow.AgentExecutionStage;

import java.util.List;

/**
 * 本轮提示词与 executionState 共用的阶段及允许动作。
 */
public record AgentTurnPromptSpec(AgentExecutionStage stage, List<AgentAllowedAction> allowedActions) {

    public static AgentTurnPromptSpec resolve(boolean taskPlanCreated,
                                              boolean hasPlannedTasks,
                                              boolean hasAnswerChunks,
                                              boolean hasObservations,
                                              boolean readyToAnswer) {
        AgentExecutionStage stage;
        if (hasAnswerChunks || readyToAnswer) {
            stage = AgentExecutionStage.ANSWERING;
        } else if (hasObservations) {
            stage = AgentExecutionStage.EVALUATING;
        } else if (!taskPlanCreated && !hasPlannedTasks) {
            stage = AgentExecutionStage.PLANNING;
        } else {
            stage = AgentExecutionStage.READY_TO_ACT;
        }
        List<AgentAllowedAction> allowed;
        if (hasAnswerChunks || readyToAnswer) {
            allowed = List.of(AgentAllowedAction.ANSWER_CHUNK, AgentAllowedAction.FINAL_ANSWER);
        } else if (!taskPlanCreated) {
            allowed = List.of(AgentAllowedAction.TASK_PLAN, AgentAllowedAction.CONFIRMATION,
                    AgentAllowedAction.FINAL_ANSWER);
        } else {
            allowed = List.of(AgentAllowedAction.TOOL_CALL, AgentAllowedAction.TOOL_BATCH,
                    AgentAllowedAction.CONFIRMATION, AgentAllowedAction.FINAL_ANSWER);
        }
        return new AgentTurnPromptSpec(stage, List.copyOf(allowed));
    }

    public List<String> actionCodes() {
        return allowedActions.stream().map(AgentAllowedAction::code).toList();
    }

    public boolean allows(AgentAllowedAction action) {
        return allowedActions.contains(action);
    }
}
