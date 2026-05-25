package com.kset.core.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.core.annotation.OpLog;
import com.kset.core.logging.LogMaskingUtil;
import com.kset.core.logging.LogUtil;
import com.kset.core.logging.OpLogContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class OpLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OpLogAspect.class);
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String userIdHeader;

    public OpLogAspect(String userIdHeader) {
        this.userIdHeader = userIdHeader != null ? userIdHeader : "X-User-Id";
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
                logOperation(joinPoint, opLog, result, error, System.currentTimeMillis() - start);
            } finally {
                OpLogContext.clear();
            }
        }
    }

    private void resolveOperator() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        String userId = request.getHeader(userIdHeader);
        if (userId != null && !userId.isBlank()) {
            OpLogContext.setOperator(userId);
        }
    }

    private void logOperation(ProceedingJoinPoint joinPoint, OpLog opLog, Object result,
                              Throwable error, long costMs) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        EvaluationContext context = buildContext(joinPoint, method, result, error);

        String targetId = eval(opLog.targetId(), context);
        String targetName = eval(opLog.targetName(), context);
        String status = error == null ? "SUCCESS" : "FAIL";
        String operator = OpLogContext.getOperator();

        if (error != null) {
            LogUtil.error(log, "operation log",
                    "type", opLog.type(),
                    "target", opLog.target(),
                    "targetId", targetId,
                    "targetName", targetName,
                    "operator", operator,
                    "status", status,
                    "costMs", costMs,
                    "error", error.getMessage());
        } else {
            LogUtil.info(log, "operation log",
                    "type", opLog.type(),
                    "target", opLog.target(),
                    "targetId", targetId,
                    "targetName", targetName,
                    "operator", operator,
                    "status", status,
                    "costMs", costMs);
        }

        if (opLog.recordParams()) {
            log.debug("oplog params: {}", maskJson(toJson(joinPoint.getArgs())));
        }
        if (opLog.recordResult() && result != null && error == null) {
            log.debug("oplog result: {}", maskJson(toJson(result)));
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
            return value != null ? String.valueOf(value) : null;
        } catch (Exception e) {
            log.warn("Failed to evaluate OpLog SpEL: {}", expression, e);
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String maskJson(String json) {
        if (json == null) {
            return null;
        }
        return LogMaskingUtil.maskJson(json);
    }
}
