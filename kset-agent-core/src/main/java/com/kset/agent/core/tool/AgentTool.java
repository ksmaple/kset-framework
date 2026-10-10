package com.kset.agent.core.tool;

import java.util.Map;

/**
 * Executable tool boundary.
 *
 * <p>Implementations may be invoked concurrently and must use
 * {@link AgentToolContext#operationId()} as the structural idempotency identity for side effects.
 * A call id identifies one operation within a run and must not be reused for a distinct operation.
 * They must persist a canonical fingerprint of the tool name and arguments, return the original
 * result for the same identity and fingerprint, and reject the same identity with a different
 * fingerprint. If a remote call may have produced a side effect but its result cannot be
 * determined, implementations must return a failed result with
 * {@link com.kset.agent.core.AgentErrorCode#TOOL_RESULT_UNKNOWN}; they must not report a definite
 * execution failure. They must also honor cancellation and the supplied deadline.
 */
public interface AgentTool {

    AgentToolDescriptor descriptor();

    ToolExecutionResult execute(Map<String, Object> arguments, AgentToolContext context);
}
