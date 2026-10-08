package com.kset.agent.core.stop;

import java.util.Optional;

/** Optional host policy that may request an additional stop, but cannot veto kernel limits. */
@FunctionalInterface
public interface AgentStopPolicy {

    Optional<AgentStopDecision> evaluate(AgentStopContext context);
}
