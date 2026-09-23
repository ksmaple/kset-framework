package com.kset.agent.sandbox;

/**
 * 沙箱执行结果
 *
 * @param success 是否成功
 * @param output  标准输出
 * @param error   错误输出
 * @param durationMs 执行时长（毫秒）
 */
public record SandboxResult(boolean success, String output, String error, long durationMs) {
}
