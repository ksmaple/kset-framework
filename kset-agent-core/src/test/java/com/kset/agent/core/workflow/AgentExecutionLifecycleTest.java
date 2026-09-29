package com.kset.agent.core.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentExecutionLifecycleTest {

    @Test
    void legalTransitionsDoNotThrow() {
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.PERCEIVING, AgentExecutionStage.PLANNING));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.PERCEIVING, AgentExecutionStage.ANSWERING));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.PLANNING, AgentExecutionStage.READY_TO_ACT));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.READY_TO_ACT, AgentExecutionStage.EXECUTING));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.READY_TO_ACT, AgentExecutionStage.EVALUATING));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.READY_TO_ACT, AgentExecutionStage.ANSWERING));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.EXECUTING, AgentExecutionStage.EVALUATING));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.EVALUATING, AgentExecutionStage.READY_TO_ACT));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.EVALUATING, AgentExecutionStage.ANSWERING));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.ANSWERING, AgentExecutionStage.PERSISTING_MEMORY));
    }

    @Test
    void illegalTransitionsThrowIllegalStateException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AgentExecutionLifecycle.requireTransition(
                        AgentExecutionStage.PERCEIVING, AgentExecutionStage.EXECUTING));
        assertEquals("非法 Agent 编排阶段转换: PERCEIVING -> EXECUTING", ex.getMessage());

        assertThrows(IllegalStateException.class,
                () -> AgentExecutionLifecycle.requireTransition(
                        AgentExecutionStage.PLANNING, AgentExecutionStage.EXECUTING));
        assertThrows(IllegalStateException.class,
                () -> AgentExecutionLifecycle.requireTransition(
                        AgentExecutionStage.EXECUTING, AgentExecutionStage.ANSWERING));
        assertThrows(IllegalStateException.class,
                () -> AgentExecutionLifecycle.requireTransition(
                        AgentExecutionStage.ANSWERING, AgentExecutionStage.PLANNING));
    }

    @Test
    void persistingMemoryIsTerminalStage() {
        for (AgentExecutionStage next : AgentExecutionStage.values()) {
            if (next == AgentExecutionStage.PERSISTING_MEMORY) {
                continue;
            }
            assertThrows(IllegalStateException.class,
                    () -> AgentExecutionLifecycle.requireTransition(
                            AgentExecutionStage.PERSISTING_MEMORY, next));
        }
    }

    @Test
    void nullOrSameStageTransitionsAreNoOp() {
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(null, AgentExecutionStage.PLANNING));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(AgentExecutionStage.PLANNING, null));
        assertDoesNotThrow(() -> AgentExecutionLifecycle.requireTransition(
                AgentExecutionStage.PERSISTING_MEMORY, AgentExecutionStage.PERSISTING_MEMORY));
    }
}
