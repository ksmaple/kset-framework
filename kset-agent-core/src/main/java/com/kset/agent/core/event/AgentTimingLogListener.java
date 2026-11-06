package com.kset.agent.core.event;

import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.action.ToolFlowPlan;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;

import java.time.Duration;

/** Logs lifecycle timing without retaining per-invocation state or request content. */
public final class AgentTimingLogListener implements AgentLifecycleListener {

    private static final System.Logger LOG = System.getLogger(AgentTimingLogListener.class.getName());
    private static final Duration SLOW_MODEL_THRESHOLD = Duration.ofSeconds(30);
    private static final Duration SLOW_TOOL_THRESHOLD = Duration.ofSeconds(10);

    @Override
    public void onEvent(AgentLifecycleContext context) {
        boolean slow = isSlow(context);
        System.Logger.Level level = switch (context.eventType()) {
            case MODEL_COMPLETED -> slow ? System.Logger.Level.WARNING : System.Logger.Level.INFO;
            case MODEL_FAILED, ACTION_FAILED, CHECKPOINT_FAILED -> System.Logger.Level.WARNING;
            case ACTION_COMPLETED -> slow ? System.Logger.Level.WARNING : System.Logger.Level.DEBUG;
            case TURN_COMPLETED, TURN_FAILED, CHECKPOINT_SAVED -> System.Logger.Level.DEBUG;
            default -> null;
        };
        if (level == null || !LOG.isLoggable(level)) {
            return;
        }
        LOG.log(level, "agent.timing operation={0} phase={1} eventStatus={2} agentRunId={3} "
                        + "invocationId={4} stepId={5} turn={6} actionIndex={7} durationMs={8} slow={9}",
                context.operation(), context.stepType(), context.eventStatus(), context.agentRunId(),
                context.invocationId(), context.stepId(), context.turn(), context.actionIndex(),
                context.elapsed().toMillis(), slow);
    }

    @Override
    public void afterRun(AgentLifecycleContext context, AgentRequest request, AgentResult result) {
        System.Logger.Level level = result.runStatus() == AgentRunStatus.FAILED
                ? System.Logger.Level.WARNING : System.Logger.Level.INFO;
        if (!LOG.isLoggable(level)) {
            return;
        }
        LOG.log(level, "agent.timing operation={0} phase=RUN runStatus={1} agentRunId={2} "
                        + "invocationId={3} stepId={4} turn={5} durationMs={6}",
                context.operation(), result.runStatus(), context.agentRunId(), context.invocationId(),
                context.stepId(), context.turn(), context.elapsed().toMillis());
    }

    private static boolean isSlow(AgentLifecycleContext context) {
        return switch (context.eventType()) {
            case MODEL_COMPLETED, MODEL_FAILED -> context.elapsed().compareTo(SLOW_MODEL_THRESHOLD) >= 0;
            case ACTION_COMPLETED, ACTION_FAILED ->
                    (StandardActionTypes.TOOL_CALL.equals(context.operation())
                            || StandardActionTypes.TOOL_BATCH.equals(context.operation())
                            || ToolFlowPlan.ACTION_TYPE.equals(context.operation()))
                            && context.elapsed().compareTo(SLOW_TOOL_THRESHOLD) >= 0;
            default -> false;
        };
    }
}
