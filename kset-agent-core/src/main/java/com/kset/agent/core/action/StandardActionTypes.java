package com.kset.agent.core.action;

/** Stable action names shared by built-in protocols and strategies. */
public final class StandardActionTypes {

    public static final String TASK_PLAN = "task_plan";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_BATCH = "tool_batch";
    public static final String ANSWER_CHUNK = "answer_chunk";
    public static final String FINAL_ANSWER = "final_answer";
    public static final String CONFIRMATION = "confirmation";

    private StandardActionTypes() {
    }
}
