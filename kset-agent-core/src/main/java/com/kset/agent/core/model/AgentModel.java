package com.kset.agent.core.model;

import com.kset.agent.core.execution.AgentExecutionContext;

/**
 * Model boundary used by the loop.
 *
 * <p>Implementations are shared by the kernel and must be thread-safe. Providers must apply the
 * supplied deadline and cancellation signal to their SDK or transport call.
 */
@FunctionalInterface
public interface AgentModel {

    ModelResponse generate(ModelRequest request, AgentExecutionContext context);
}
