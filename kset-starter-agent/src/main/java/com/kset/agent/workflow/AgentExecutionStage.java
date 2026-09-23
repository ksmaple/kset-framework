package com.kset.agent.workflow;

/** Agent 内部编排阶段。 */
public enum AgentExecutionStage {
    PERCEIVING("perceiving"),
    PLANNING("planning"),
    READY_TO_ACT("ready_to_act"),
    EXECUTING("executing"),
    EVALUATING("evaluating"),
    ANSWERING("answering"),
    PERSISTING_MEMORY("persisting_memory");

    private final String code;

    AgentExecutionStage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
