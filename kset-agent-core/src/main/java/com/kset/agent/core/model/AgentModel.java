package com.kset.agent.core.model;

import com.kset.agent.core.execution.AgentExecutionContext;

/**
 * Model boundary used by the loop.
 *
 * <p>Implementations are shared by the kernel and must be thread-safe. Providers must apply the
 * supplied deadline and cancellation signal to their SDK or transport call. Adapters interpret
 * strategy-supplied {@link ModelRequest#attributes()} selectively and validate the actual provider
 * token window after constructing the provider request.
 */
@FunctionalInterface
public interface AgentModel {

    ModelResponse generate(ModelRequest request, AgentExecutionContext context);
}
