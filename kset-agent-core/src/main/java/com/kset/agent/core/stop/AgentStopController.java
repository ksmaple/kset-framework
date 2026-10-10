package com.kset.agent.core.stop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.api.AgentRunStatus;

import java.util.List;
import java.util.Optional;

/** Centralized stop evaluator. Mandatory limits run before all extension policies. */
public final class AgentStopController {

    private final List<AgentStopPolicy> policies;

    public AgentStopController(List<AgentStopPolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public AgentStopDecision evaluate(AgentStopContext context) {
        AgentStopDecision immediate = evaluateImmediate(context);
        if (immediate.shouldStop()) {
            return immediate;
        }
        if (context.state().runStatus() == AgentRunStatus.COMPLETED) {
            return AgentStopDecision.stop(AgentStopReason.COMPLETED,
                    AgentRunStatus.COMPLETED, "final answer completed");
        }
        if (context.state().runStatus() == AgentRunStatus.SUSPENDED) {
            return AgentStopDecision.stop(AgentStopReason.WAITING_INPUT,
                    AgentRunStatus.SUSPENDED, "external input required");
        }
        if (context.state().runStatus() == AgentRunStatus.FAILED) {
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
        return evaluatePolicies(context);
    }

    /** Checks action-sensitive limits and host policies between actions in the same turn. */
    public AgentStopDecision evaluateAfterAction(AgentStopContext context) {
        AgentStopDecision immediate = evaluateImmediate(context);
        if (immediate.shouldStop()) {
            return immediate;
        }
        if (context.state().consecutiveNoProgress()
                >= context.request().options().noProgressLimit()) {
            return AgentStopDecision.stop(AgentStopReason.NO_PROGRESS_LIMIT,
                    AgentRunStatus.FAILED, "no-progress limit exceeded");
        }
        return evaluatePolicies(context);
    }

    /** Checks cancellation and elapsed time without applying turn or progress limits. */
    public AgentStopDecision evaluateImmediate(AgentStopContext context) {
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            return AgentStopDecision.stop(AgentStopReason.THREAD_INTERRUPTED,
                    AgentRunStatus.CANCELLED, "thread interrupted");
        }
        if (context.executionContext().cancellation().isCancellationRequested()) {
            return AgentStopDecision.stop(AgentStopReason.USER_CANCELLED,
                    AgentRunStatus.CANCELLED, "cancellation requested");
        }
        if (!context.now().isBefore(context.executionContext().deadline())) {
            return AgentStopDecision.stop(AgentStopReason.TIMEOUT,
                    AgentRunStatus.FAILED, "active execution timeout exceeded");
        }
        return AgentStopDecision.continueRun();
    }

    /** Converts an unexpected kernel failure into the canonical fatal stop decision. */
    public AgentStopDecision fatal(RuntimeException error) {
        String stopMessage = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        return AgentStopDecision.stop(
                AgentStopReason.FATAL_ERROR, AgentRunStatus.FAILED, stopMessage);
    }

    private AgentStopDecision evaluatePolicies(AgentStopContext context) {
        for (AgentStopPolicy policy : policies) {
            Optional<AgentStopDecision> evaluated;
            try {
                evaluated = policy.evaluate(context);
            } catch (RuntimeException error) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "stop policy failed: " + policy.getClass().getName(), error);
            }
            if (evaluated == null) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "stop policy returned null: " + policy.getClass().getName());
            }
            AgentStopDecision decision = evaluated.orElse(null);
            if (decision != null && decision.shouldStop()) {
                if (decision.runStatus() == AgentRunStatus.COMPLETED) {
                    throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                            "stop policy cannot complete a run without a final answer");
                }
                return AgentStopDecision.stop(
                        AgentStopReason.POLICY, decision.runStatus(),
                        decision.stopMessage());
            }
        }
        return AgentStopDecision.continueRun();
    }
}
