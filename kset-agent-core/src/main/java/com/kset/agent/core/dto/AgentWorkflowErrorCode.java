package com.kset.agent.core.dto;

import java.util.Arrays;
import java.util.Optional;

/**
 * 核心 Agent 编排错误契约。
 *
 * <p>错误码供外层稳定识别，默认提示和默认回答用于外层尚未配置自定义文案时回退。
 */
public enum AgentWorkflowErrorCode {

    AGENT_WORKFLOW_EXECUTION_FAILED("工作流执行失败", "处理失败，请稍后重试。"),
    AGENT_WORKFLOW_TASK_NOT_FOUND("工作流任务不存在", "未找到对应任务，请重新发起。"),
    AGENT_WORKFLOW_STATE_INVALID("工作流任务状态不允许当前操作", "当前任务状态已变化，请刷新后重试。"),
    AGENT_WORKFLOW_ACCESS_DENIED("无权访问当前工作流任务", "当前账号无权执行此操作。"),
    AGENT_WORKFLOW_PROJECT_UNAVAILABLE("工作流关联项目不可用", "当前项目不可用，请切换项目后重试。"),
    AGENT_WORKFLOW_AUTH_EXPIRED("工作流认证上下文已失效", "登录状态已失效，请重新登录后重试。"),
    AGENT_WORKFLOW_RESULT_UNKNOWN("工具节点执行结果未知", "执行结果尚未确认，请核验后再继续。"),
    AGENT_WORKFLOW_LEASE_CONFLICT("工作流任务已由其他实例接管", "任务正在由其他实例处理，请稍后查看结果。"),
    AGENT_WORKFLOW_SNAPSHOT_INVALID("工作流恢复快照不可用", "任务恢复失败，请重新发起。"),
    AGENT_MEMORY_PERSISTENCE_FAILED("回答记忆保存失败", "结果已生成，但保存会话记忆失败，请稍后恢复任务。"),
    AGENT_EXECUTION_INTERRUPTED("Agent 执行已中断", "任务已停止。"),
    AGENT_EXECUTION_CANCELLED("Agent 任务已取消", "任务已取消。"),
    AGENT_EXECUTION_TIMEOUT("Agent 执行超时", "处理超时，请稍后重试。"),
    AGENT_MODEL_TOKEN_LIMIT_EXCEEDED("模型 Token 限额已耗尽", "当前内容超过模型处理能力，请缩小范围后重试。"),
    AGENT_MODEL_RATE_LIMITED("模型服务请求受限", "模型服务繁忙，请稍后重试。"),
    AGENT_MODEL_SERVICE_UNAVAILABLE("模型服务暂时不可用", "模型服务暂时不可用，请稍后重试。"),
    AGENT_MODEL_REQUEST_REJECTED("模型服务拒绝了当前请求", "当前请求无法由模型处理，请检查模型配置或调整内容。"),
    AGENT_MODEL_CALL_FAILED("模型服务调用失败", "模型处理失败，请稍后重试。"),
    AGENT_MODEL_RESPONSE_EMPTY("模型响应为空", "模型未返回可处理内容，请稍后重试。"),
    AGENT_MODEL_RESPONSE_BOUNDARY_INVALID("模型响应协议边界无效", "模型返回的协议边界异常，请稍后重试。"),
    AGENT_MODEL_RESPONSE_JSON_INVALID("模型响应 JSON 无效", "模型返回的数据格式异常，请稍后重试。"),
    AGENT_MODEL_RESPONSE_TOO_LARGE("模型响应超过协议限制", "模型返回内容过长，请缩小处理范围后重试。"),
    AGENT_MODEL_RESPONSE_SCHEMA_INVALID("模型响应字段结构无效", "模型返回的字段结构异常，请稍后重试。"),
    AGENT_MODEL_RESPONSE_TRUNCATED("模型响应未完整生成", "模型回答未完整生成，请缩小处理范围后重试。"),
    AGENT_MODEL_RESPONSE_INVALID("模型响应不符合编排协议", "模型返回格式异常，请稍后重试。"),
    AGENT_OUTPUT_CONVERTER_UNAVAILABLE("Agent 输出协议转换器不可用", "当前输出协议暂不可用，请稍后重试。"),
    AGENT_PROTOCOL_RETRY_EXHAUSTED("模型响应连续违反编排协议", "模型未能生成有效结果，请稍后重试。"),
    AGENT_TOOL_UNAVAILABLE("编排所需工具不可用", "当前工具不可用，无法继续处理。"),
    AGENT_TOOL_EXECUTION_FAILED("编排工具执行失败", "工具执行失败，请稍后重试。"),
    AGENT_EVIDENCE_INSUFFICIENT("当前证据不足以完成回答", "当前信息不足以形成可靠回答，请补充具体范围后重试。"),
    AGENT_EVIDENCE_STALLED("连续查询未获得新证据", "当前范围内未找到更多有效信息，请补充文件、模块或关键词。"),
    AGENT_MAX_STEPS_EXCEEDED("Agent 已达到最大编排轮次", "处理范围超过本轮执行上限，请缩小范围后重试。"),
    AGENT_TASK_PLAN_INVALID("Agent 任务计划无效", "任务拆解失败，请调整目标后重试。"),
    AGENT_CONFIRMED_TOOL_UNAVAILABLE("已确认工具当前不可用", "已确认的工具当前不可用，请重新发起操作。"),
    AGENT_CONFIRMATION_ACCESS_DENIED("无权处理当前确认任务", "当前账号无权处理此确认任务。"),
    AGENT_CONFIRMATION_EXPIRED("确认信息已失效", "确认信息已过期，请重新发起请求。"),
    AGENT_CONFIRMATION_INVALID("确认输入不符合要求", "确认内容无效，请检查后重新提交。"),
    AGENT_CONFIRMATION_INPUT_TOO_LONG("确认补充输入超过限制", "补充输入内容过长，请缩短后重新提交。");

    private final String defaultMessage;
    private final String defaultAnswer;

    AgentWorkflowErrorCode(String defaultMessage, String defaultAnswer) {
        this.defaultMessage = defaultMessage;
        this.defaultAnswer = defaultAnswer;
    }

    public String code() {
        return name();
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public String defaultAnswer() {
        return defaultAnswer;
    }

    public static Optional<AgentWorkflowErrorCode> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(code))
                .findFirst();
    }
}
