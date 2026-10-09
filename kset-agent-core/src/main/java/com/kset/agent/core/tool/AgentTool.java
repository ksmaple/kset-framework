package com.kset.agent.core.tool;

import java.util.Map;

/**
 * Executable tool boundary.
 *
 * <p>Implementations may be invoked concurrently and must use {@link AgentToolContext#callId()} as
 * the idempotency key for side effects. They must also honor cancellation and the supplied deadline.
 */
public interface AgentTool {

    AgentToolDescriptor descriptor();

    ToolExecutionResult execute(Map<String, Object> arguments, AgentToolContext context);
}
