package com.kset.agent.core.model;

import com.kset.agent.core.execution.AgentExecutionContext;

import java.util.Objects;

/**
 * Model boundary used by the loop.
 *
 * <p>Implementations are shared by the kernel and must be thread-safe. Providers must apply the
 * supplied deadline and cancellation signal to their SDK or transport call. Adapters interpret
 * strategy-supplied {@link ModelRequest#attributes()} selectively and validate the actual provider
 * token window after constructing the provider request. Report the selected model through
 * {@link #modelName(ModelRequest)} to apply built-in limits. Implement
 * {@link AgentModelCapacity} to let the kernel compact prompts before that call.
 */
@FunctionalInterface
public interface AgentModel {

    ModelResponse generate(ModelRequest request, AgentExecutionContext context);

    /** Exact provider model ID selected for this request, or null when the adapter cannot report it. */
    default String modelName(ModelRequest request) {
        return null;
    }

    /** Binds a fixed provider model ID to an adapter without changing its functional signature. */
    static AgentModel named(String modelName, AgentModel delegate) {
        Objects.requireNonNull(delegate, "delegate");
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        String name = modelName.trim();
        if (delegate instanceof AgentModelCapacity capacity) {
            return new AgentModelCapacity() {
                @Override
                public String modelName(ModelRequest request) {
                    return name;
                }

                @Override
                public ModelPromptCapacity promptCapacity(ModelRequest request) {
                    return capacity.promptCapacity(request);
                }

                @Override
                public ModelResponse generate(ModelRequest request, AgentExecutionContext context) {
                    return delegate.generate(request, context);
                }
            };
        }
        return new AgentModel() {
            @Override
            public String modelName(ModelRequest request) {
                return name;
            }

            @Override
            public ModelResponse generate(ModelRequest request, AgentExecutionContext context) {
                return delegate.generate(request, context);
            }
        };
    }
}
