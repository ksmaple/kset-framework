package com.kset.agent.workflow;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Agent 任务级编排阶段转换规则。 */
public final class AgentExecutionLifecycle {

    private static final Map<AgentExecutionStage, Set<AgentExecutionStage>> TRANSITIONS = buildTransitions();

    private AgentExecutionLifecycle() {
    }

    public static void requireTransition(AgentExecutionStage current, AgentExecutionStage next) {
        if (next == null || current == next || current == null) {
            return;
        }
        Set<AgentExecutionStage> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalStateException("非法 Agent 编排阶段转换: " + current + " -> " + next);
        }
    }

    private static Map<AgentExecutionStage, Set<AgentExecutionStage>> buildTransitions() {
        Map<AgentExecutionStage, Set<AgentExecutionStage>> transitions =
                new EnumMap<>(AgentExecutionStage.class);
        transitions.put(AgentExecutionStage.PERCEIVING,
                EnumSet.of(AgentExecutionStage.PLANNING, AgentExecutionStage.ANSWERING));
        transitions.put(AgentExecutionStage.PLANNING,
                EnumSet.of(AgentExecutionStage.READY_TO_ACT, AgentExecutionStage.ANSWERING));
        transitions.put(AgentExecutionStage.READY_TO_ACT,
                EnumSet.of(AgentExecutionStage.EXECUTING, AgentExecutionStage.EVALUATING,
                        AgentExecutionStage.ANSWERING));
        transitions.put(AgentExecutionStage.EXECUTING,
                EnumSet.of(AgentExecutionStage.EVALUATING));
        transitions.put(AgentExecutionStage.EVALUATING,
                EnumSet.of(AgentExecutionStage.READY_TO_ACT, AgentExecutionStage.ANSWERING));
        transitions.put(AgentExecutionStage.ANSWERING,
                EnumSet.of(AgentExecutionStage.PERSISTING_MEMORY));
        transitions.put(AgentExecutionStage.PERSISTING_MEMORY,
                EnumSet.noneOf(AgentExecutionStage.class));
        return Map.copyOf(transitions);
    }
}
