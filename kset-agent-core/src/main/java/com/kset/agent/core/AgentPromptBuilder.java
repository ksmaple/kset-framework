package com.kset.agent.core;

import com.kset.agent.core.config.AgentOrchestrationProperties;
import com.kset.agent.core.dto.ChatDebugInfo;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

@Component
public class AgentPromptBuilder {

    private final AgentOutputBudgetService outputBudgetService;
    private final AgentOrchestrationProperties orchestrationProperties;

    public AgentPromptBuilder(AgentOutputBudgetService outputBudgetService,
                              AgentOrchestrationProperties orchestrationProperties) {
        this.outputBudgetService = outputBudgetService;
        this.orchestrationProperties = orchestrationProperties;
    }

    public AgentPromptSnapshot buildJsonAgentPromptSnapshot(AgentTurnPromptSpec spec) {
        int maxAnswerChars = outputBudgetService.current().maxAnswerCharsPerRound();
        String systemPrompt = AgentProtocolDefinition.systemPrompt(
                spec, maxAnswerChars, orchestrationProperties.getMaxPlanTasks());
        String structuredFormat = AgentProtocolDefinition.structuredOutputFormat(spec, maxAnswerChars);
        return assemblePromptSnapshot(systemPrompt, structuredFormat, spec);
    }

    /**
     * 回滚保留：feature-key=agent-core-system-prompt，change-id=prompt-20260902-v3。
     * 保留原因：code-v2 整包系统词组装被阶段叠加替代；生产路径不得调用。
     */
    AgentPromptSnapshot buildJsonAgentPromptSnapshotV2ForRollback() {
        int maxAnswerChars = outputBudgetService.current().maxAnswerCharsPerRound();
        String systemPrompt = AgentProtocolDefinition.systemPromptV2ForRollback(maxAnswerChars);
        String structuredFormat = AgentProtocolDefinition.structuredOutputFormatForRollback(maxAnswerChars);
        return assemblePromptSnapshot(systemPrompt, structuredFormat, null);
    }

    /**
     * 回滚保留：feature-key=agent-core-system-prompt，change-id=prompt-20260902-v2。
     * 保留原因：code-v1 核心编排系统提示词组装被后续版本替代；生产路径不得调用。
     */
    AgentPromptSnapshot buildJsonAgentPromptSnapshotForRollback() {
        int maxAnswerChars = outputBudgetService.current().maxAnswerCharsPerRound();
        String systemPrompt = AgentProtocolDefinition.systemPromptForRollback(maxAnswerChars);
        String structuredFormat = AgentProtocolDefinition.structuredOutputFormatForRollback(maxAnswerChars);
        return assemblePromptSnapshot(systemPrompt, structuredFormat, null);
    }

    private AgentPromptSnapshot assemblePromptSnapshot(String systemPrompt,
                                                       String structuredFormat,
                                                       AgentTurnPromptSpec spec) {
        String stageLabel = spec == null ? "" : "/" + spec.stage().code();
        String resolvedSystemPrompt = systemPrompt.replace(
                AgentProtocolDefinition.IDENTITY_PLACEHOLDER, orchestrationProperties.getIdentity());
        String content = resolvedSystemPrompt + "\n\n结构化返回格式：\n" + structuredFormat;
        return new AgentPromptSnapshot(content, List.of(
                buildCodePromptDebug("agent_global_system", "Agent 全局系统规则" + stageLabel,
                        resolvedSystemPrompt),
                buildCodePromptDebug("agent_structured_output_format", "Agent 结构化返回格式" + stageLabel,
                        structuredFormat)));
    }

    private ChatDebugInfo.PromptDebug buildCodePromptDebug(String code, String name, String content) {
        ChatDebugInfo.PromptDebug debug = new ChatDebugInfo.PromptDebug();
        debug.setPromptCode(code);
        debug.setPromptName(name);
        debug.setVersionNo(AgentProtocolDefinition.VERSION);
        debug.setRenderedContent(content);
        debug.setVariables(new LinkedHashMap<>());
        return debug;
    }

    public record AgentPromptSnapshot(String systemPrompt, List<ChatDebugInfo.PromptDebug> promptDebug) {
    }

}
