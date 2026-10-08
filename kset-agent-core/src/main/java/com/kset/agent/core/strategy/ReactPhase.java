package com.kset.agent.core.strategy;

/** Internal phase used only by the default ReAct strategy. */
public enum ReactPhase {
    PLANNING,
    ACTING,
    EVALUATING,
    ANSWERING
}
