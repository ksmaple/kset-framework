package com.kset.agent.core;

import com.kset.agent.model.AiModelProvider;
import org.springframework.stereotype.Service;

/**
 * 根据当前模型输出 Token 配置计算 Agent 单轮协议和正文字符预算。
 */
@Service
public class AgentOutputBudgetService {

    private static final int MIN_CHINESE_CHARS_PER_TOKEN = 1;

    private final AiModelProvider aiModelProvider;

    public AgentOutputBudgetService(AiModelProvider aiModelProvider) {
        this.aiModelProvider = aiModelProvider;
    }

    public OutputBudget current() {
        int configuredTokens = aiModelProvider.effectiveMaxOutputTokens(0);
        if (configuredTokens <= 0) {
            throw new IllegalStateException("当前激活模型 maxTokens 必须大于 0");
        }
        int maxOutputTokens = configuredTokens;
        int outputChars = maxOutputTokens * MIN_CHINESE_CHARS_PER_TOKEN;
        int protocolChars = Math.max(1, outputChars - AgentProtocolDefinition.markerChars());
        int answerChars = Math.max(1, outputChars - AgentProtocolDefinition.maxAnswerEnvelopeChars());
        int answerRounds = AgentProtocolDefinition.MAX_ANSWER_CHUNKS + 1;
        int mergedAnswerChars = (int) Math.min(Integer.MAX_VALUE, (long) answerChars * answerRounds);
        return new OutputBudget(maxOutputTokens, protocolChars, answerChars, mergedAnswerChars);
    }

    public OutputBudget forInputChars(int inputChars) {
        int estimatedInputTokens = Math.max(1, (int) Math.ceil(Math.max(0, inputChars) / 2.0));
        int effectiveTokens = aiModelProvider.effectiveMaxOutputTokens(estimatedInputTokens);
        if (effectiveTokens <= 0) {
            return current();
        }
        int outputChars = effectiveTokens;
        int protocolChars = Math.max(1, outputChars - AgentProtocolDefinition.markerChars());
        int answerChars = Math.max(1, outputChars - AgentProtocolDefinition.maxAnswerEnvelopeChars());
        int mergedAnswerChars = (int) Math.min(Integer.MAX_VALUE,
                (long) answerChars * (AgentProtocolDefinition.MAX_ANSWER_CHUNKS + 1));
        return new OutputBudget(effectiveTokens, protocolChars, answerChars, mergedAnswerChars);
    }

    public record OutputBudget(int maxOutputTokens, int maxProtocolChars,
                               int maxAnswerCharsPerRound, int maxMergedAnswerChars) {
    }
}
