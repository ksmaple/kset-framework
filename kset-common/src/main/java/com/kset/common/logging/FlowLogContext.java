package com.kset.common.logging;

import com.kset.common.monitor.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;


public final class FlowLogContext {

    private static final Logger log = LoggerFactory.getLogger(FlowLogContext.class);

    private static final String FLOW_ID_KEY = "flow.instanceId";
    private static final String FLOW_STEP_KEY = "flow.step";
    private static final String FLOW_STEP_NAME_KEY = "flow.stepName";
    private static final String FLOW_EVENT_TYPE_KEY = "flow.eventType";

    private FlowLogContext() {
    }

    
    public static String beginFlow(String flowType, String bizKey) {
        if (Monitor.currentTraceId().isEmpty() && log.isDebugEnabled()) {
            log.debug("FlowLogContext.beginFlow without bound traceId; ensure Monitor.bindHttpIncoming or Filter runs first");
        }
        String flowId = flowType + "-" + bizKey + "-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put(FLOW_ID_KEY, flowId);
        MDC.put(FLOW_STEP_KEY, "0");
        return flowId;
    }

    /**
     * 记录一个步骤。步骤号自动递增。
     *
     * @param stepName  步骤名称，snake_case
     * @param eventType 事件类型
     */
    public static void step(String stepName, FlowEventType eventType) {
        int currentStep = 0;
        String existing = MDC.get(FLOW_STEP_KEY);
        if (existing != null) {
            try {
                currentStep = Integer.parseInt(existing);
            } catch (NumberFormatException ignored) {
            }
        }
        if (eventType == FlowEventType.ENTER) {
            currentStep++;
        }
        MDC.put(FLOW_STEP_KEY, String.valueOf(currentStep));
        MDC.put(FLOW_STEP_NAME_KEY, stepName);
        MDC.put(FLOW_EVENT_TYPE_KEY, eventType.name());
    }

    
    public static void endFlow() {
        step("end", FlowEventType.EXIT);
    }

    
    public static void error(String stepName, Throwable t) {
        step(stepName, FlowEventType.ERROR);
        if (t != null) {
            MDC.put("flow.errorType", t.getClass().getSimpleName());
        }
    }

    /**
     * 清除所有 flow 相关的 MDC 字段（不调用 {@link Monitor#clear()}，避免误删 traceId）。
     */
    public static void clear() {
        MDC.remove(FLOW_ID_KEY);
        MDC.remove(FLOW_STEP_KEY);
        MDC.remove(FLOW_STEP_NAME_KEY);
        MDC.remove(FLOW_EVENT_TYPE_KEY);
        MDC.remove("flow.errorType");
    }

    
    public enum FlowEventType {
        ENTER,
        STATE_CHANGE,
        BRANCH,
        EXT_CALL,
        EXIT,
        ERROR
    }
}
