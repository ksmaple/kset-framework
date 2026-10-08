package com.kset.agent.core.stop;

import com.kset.agent.core.api.AgentRunStatus;

import java.time.Duration;
import java.util.List;

/** Centralized stop evaluator. Mandatory limits run before all extension policies. */
public final class AgentStopController {

    private final List<AgentStopPolicy> policies;

    public AgentStopController(List<AgentStopPolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public AgentStopDecision evaluate(AgentStopContext context) {
        AgentStopDecision immediate = evaluateImmediate(context);
        if (immediate.stop()) {
            return immediate;
        }
        if (context.state().status() == AgentRunStatus.COMPLETED) {
            return AgentStopDecision.stop(AgentStopReason.COMPLETED,
                    AgentRunStatus.COMPLETED, "final answer completed");
        }
        if (context.state().status() == AgentRunStatus.SUSPENDED) {
            return AgentStopDecision.stop(AgentStopReason.WAITING_INPUT,
                    AgentRunStatus.SUSPENDED, "external input required");
        }
        if (context.state().status() == AgentRunStatus.FAILED) {
            return AgentStopDecision.stop(AgentStopReason.FATAL_ERROR,
                    AgentRunStatus.FAILED, "run failed");
        }
        if (context.state().turn() >= context.request().options().maxTurns()) {
            return AgentStopDecision.stop(AgentStopReason.MAX_TURNS,
                    AgentRunStatus.FAILED, "maximum turns exceeded");
        }
        if (context.state().consecutiveProtocolErrors()
                >= context.request().options().protocolErrorLimit()) {
            return AgentStopDecision.stop(AgentStopReason.PROTOCOL_ERROR_LIMIT,
                    AgentRunStatus.FAILED, "protocol error limit exceeded");
        }
        if (context.state().consecutiveNoProgress()
                >= context.request().options().noProgressLimit()) {
            return AgentStopDecision.stop(AgentStopReason.NO_PROGRESS_LIMIT,
                    AgentRunStatus.FAILED, "no-progress limit exceeded");
        }
        for (AgentStopPolicy policy : policies) {
            AgentStopDecision decision = policy.evaluate(context).orElse(null);
            if (decision != null && decision.stop()) {
                return decision.reason() == AgentStopReason.NONE
                        ? AgentStopDecision.stop(AgentStopReason.POLICY, decision.status(), decision.detail())
                        : decision;
            }
        }
        return AgentStopDecision.continueRun();
    }

    /** Checks cancellation and elapsed time without applying turn or progress limits. */
    public AgentStopDecision evaluateImmediate(AgentStopContext context) {
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            return AgentStopDecision.stop(AgentStopReason.THREAD_INTERRUPTED,
                    AgentRunStatus.CANCELLED, "thread interrupted");
        }
        if (context.request().cancellation().isCancellationRequested()) {
            return AgentStopDecision.stop(AgentStopReason.USER_CANCELLED,
                    AgentRunStatus.CANCELLED, "cancellation requested");
        }
        Duration elapsed = Duration.between(context.state().startedAt(), context.now());
        if (elapsed.compareTo(context.request().options().timeout()) >= 0) {
            return AgentStopDecision.stop(AgentStopReason.TIMEOUT,
                    AgentRunStatus.FAILED, "run timeout exceeded");
        }
        return AgentStopDecision.continueRun();
    }

    /** Converts an unexpected kernel failure into the canonical fatal stop decision. */
    public AgentStopDecision fatal(RuntimeException error) {
        String detail = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        return AgentStopDecision.stop(AgentStopReason.FATAL_ERROR, AgentRunStatus.FAILED, detail);
    }
}
