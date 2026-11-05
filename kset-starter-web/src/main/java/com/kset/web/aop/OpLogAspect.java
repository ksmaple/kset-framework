package com.kset.web.aop;

import com.kset.common.auth.LoginContext;
import com.kset.common.logging.OpLogContext;
import com.kset.common.logging.StructLog;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.web.logging.RequestLogParameters;
import com.kset.web.annotation.OpLog;
import com.kset.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * {@link OpLog} 操作日志切面。
 */
@Aspect
@Slf4j
public class OpLogAspect {

    private static final StructLog OP_LOG = StructLog.of(OpLogAspect.class);

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    public OpLogAspect() {
    }

    /** Kept for existing manual configuration; operator identity now comes from LoginContext. */
    @Deprecated
    public OpLogAspect(String ignoredUserIdHeader) {
        this();
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        resolveOperator();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                try {
                    logOperation(joinPoint, opLog, result, error, System.currentTimeMillis() - start);
                } catch (RuntimeException ignored) {
                    // Diagnostics must not replace the operation result or its original error.
                }
            } finally {
                OpLogContext.clear();
            }
        }
    }

    private void resolveOperator() {
        LoginContext.currentUserId().ifPresent(OpLogContext::setOperator);
    }

    private void logOperation(ProceedingJoinPoint joinPoint, OpLog opLog, Object result,
                              Throwable error, long costMs) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        EvaluationContext context = buildContext(joinPoint, method, result, error);

        String targetId = eval(opLog.targetId(), context);
        Integer businessCode = result instanceof ApiResponse<?> response ? response.getCode() : null;
        String status = error == null && (businessCode == null || businessCode == 0) ? "SUCCESS" : "FAIL";
        String operator = OpLogContext.getOperator();
        String subjectType = LoginContext.currentSubjectType().orElse(null);

        String traceId = Monitor.currentTraceId().orElse(null);
        if (error != null) {
            OP_LOG.error("operation log",
                    "type", opLog.type(),
                    "target", opLog.target(),
                    "targetId", targetId,
                    "operator", operator,
                    "subjectType", subjectType,
                    "status", status,
                    "businessCode", businessCode,
                    "costMs", costMs,
                    "traceId", traceId,
                    "errorType", error.getClass().getName());
        } else if (businessCode != null && businessCode != 0) {
            OP_LOG.warn("operation log",
                    "type", opLog.type(),
                    "target", opLog.target(),
                    "targetId", targetId,
                    "operator", operator,
                    "subjectType", subjectType,
                    "status", status,
                    "businessCode", businessCode,
                    "costMs", costMs,
                    "traceId", traceId);
        } else {
            OP_LOG.info("operation log",
                    "type", opLog.type(),
                    "target", opLog.target(),
                    "targetId", targetId,
                    "operator", operator,
                    "subjectType", subjectType,
                    "status", status,
                    "businessCode", businessCode,
                    "costMs", costMs,
                    "traceId", traceId);
        }
        if (costMs > 500) {
            Monitor.logEvent(MonitorTypes.BIZ, "oplog", MonitorStatus.FAIL,
                    costMs + "ms " + opLog.type() + ":" + opLog.target());
        }

        if (opLog.recordParams() && log.isDebugEnabled()) {
            log.debug("operation params type={} target={} {}", opLog.type(), opLog.target(),
                    parameterSummary());
        }
        if (opLog.recordResult() && result != null && error == null && log.isDebugEnabled()) {
            log.debug("operation result type={} target={} resultType={}",
                    opLog.type(), opLog.target(), result.getClass().getSimpleName());
        }
    }

    private EvaluationContext buildContext(ProceedingJoinPoint joinPoint, Method method,
                                           Object result, Throwable error) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        context.setVariable("result", result);
        context.setVariable("error", error);
        return context;
    }

    private String eval(String expression, EvaluationContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Expression exp = PARSER.parseExpression(expression);
            Object value = exp.getValue(context);
            if (!(value instanceof String || value instanceof Number || value instanceof java.util.UUID)) {
                return null;
            }
            String text = String.valueOf(value);
            return text.length() <= 128 && text.matches("[A-Za-z0-9_.:-]+") ? text : null;
        } catch (Exception e) {
            log.warn("Failed to evaluate OpLog SpEL: {}", expression, e);
            return null;
        }
    }

    private String parameterSummary() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return RequestLogParameters.capture(attrs.getRequest());
        }
        return "parameters=unavailable";
    }
}
