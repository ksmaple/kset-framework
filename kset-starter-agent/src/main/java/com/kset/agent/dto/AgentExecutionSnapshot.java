package com.kset.agent.dto;

import com.kset.agent.workflow.AgentExecutionStage;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 不包含模型原文和思维链的版本化 Agent 恢复快照。 */
@Data
public class AgentExecutionSnapshot {

    public static final int CURRENT_VERSION = 3;

    private int version = CURRENT_VERSION;
    private int stepNo;
    private boolean taskPlanCreated;
    private List<String> plannedTaskIds = new ArrayList<>();
    private List<AgentWorkflowResult.PlanTask> planTasks = new ArrayList<>();
    private AgentWorkflowResult.QueryAnalysis queryAnalysis;
    private List<Map<String, Object>> observations = new ArrayList<>();
    private Map<String, ToolObservation> toolCache = new LinkedHashMap<>();
    private Set<String> evidenceFingerprints = new LinkedHashSet<>();
    private List<String> answerChunks = new ArrayList<>();
    private int consecutiveNoNewEvidence;
    private int duplicatePlanCount;
    private int protocolErrorCount;
    private boolean thresholdNoticeSent;
    private String protocolErrorForRetry;
    private String memoryPriority;
    private String memorySummary;
    private AgentExecutionStage executionStage;
}
