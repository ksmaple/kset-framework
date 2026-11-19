package com.kset.agent.harness;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Records only fixed phase names, terminal signals and duration. */
public final class AgentHarnessTimingMiddleware implements MiddlewareBase {
    private static final System.Logger LOG = System.getLogger(AgentHarnessTimingMiddleware.class.getName());

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        return observe("agent", 0, () -> next.apply(input));
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return observe("model", 30_000, () -> next.apply(input));
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        return observe("tool", 10_000, () -> next.apply(input));
    }

    private Flux<AgentEvent> observe(String phase, long slowAfterMs,
                                     java.util.function.Supplier<Flux<AgentEvent>> call) {
        return Flux.defer(() -> {
            long started = System.nanoTime();
            try {
                return call.get().doFinally(signal -> log(phase, signal, started, slowAfterMs));
            } catch (RuntimeException error) {
                log(phase, SignalType.ON_ERROR, started, slowAfterMs);
                return Flux.error(error);
            }
        });
    }

    private void log(String phase, SignalType signal, long started, long slowAfterMs) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        boolean slow = slowAfterMs > 0 && durationMs >= slowAfterMs;
        System.Logger.Level level = signal == SignalType.ON_ERROR || slow
                ? System.Logger.Level.WARNING
                : "tool".equals(phase) ? System.Logger.Level.DEBUG : System.Logger.Level.INFO;
        if (LOG.isLoggable(level)) {
            LOG.log(level, "agent.harness.timing phase={0} status={1} durationMs={2} slow={3}",
                    phase, signal, durationMs, slow);
        }
    }
}
