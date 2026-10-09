package com.kset.agent.core.stop;

import java.util.Optional;

/**
 * Optional thread-safe host policy that may request an additional stop but cannot veto kernel
 * limits. The kernel exposes every accepted policy stop with {@link AgentStopReason#POLICY}.
 */
@FunctionalInterface
public interface AgentStopPolicy {

    Optional<AgentStopDecision> evaluate(AgentStopContext context);
}
