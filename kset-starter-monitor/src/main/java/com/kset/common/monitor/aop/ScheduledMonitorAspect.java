package com.kset.common.monitor.aop;

import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring scheduled task monitor aspect.
 */
@Aspect
public class ScheduledMonitorAspect {

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) || " +
            "@annotation(org.springframework.scheduling.annotation.Schedules)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        String name = className + "." + methodName;
        try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.SCHEDULED_TASK, name)) {
            tx.addData("component", "scheduled");
            tx.addData("class", method.getDeclaringClass().getName());
            tx.addData("method", methodName);
            tx.addData("schedule", describeSchedules(method));
            try {
                Object result = joinPoint.proceed();
                tx.setStatus(MonitorStatus.SUCCESS);
                return result;
            } catch (Throwable e) {
                tx.setStatus(e);
                Monitor.logError(e, name + " failed");
                throw e;
            }
        }
    }

    static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        if (target == null) {
            return method;
        }
        return AopUtils.getMostSpecificMethod(method, target.getClass());
    }

    static String describeSchedules(Method method) {
        Scheduled[] scheduledAnnotations = method.getAnnotationsByType(Scheduled.class);
        if (scheduledAnnotations.length == 0) {
            return "";
        }
        List<String> descriptions = new ArrayList<>(scheduledAnnotations.length);
        for (Scheduled scheduled : scheduledAnnotations) {
            descriptions.add(describeSchedule(scheduled));
        }
        return String.join(";", descriptions);
    }

    private static String describeSchedule(Scheduled scheduled) {
        List<String> parts = new ArrayList<>();
        addString(parts, "cron", scheduled.cron());
        addString(parts, "zone", scheduled.zone());
        addLong(parts, "fixedDelay", scheduled.fixedDelay());
        addString(parts, "fixedDelayString", scheduled.fixedDelayString());
        addLong(parts, "fixedRate", scheduled.fixedRate());
        addString(parts, "fixedRateString", scheduled.fixedRateString());
        addLong(parts, "initialDelay", scheduled.initialDelay());
        addString(parts, "initialDelayString", scheduled.initialDelayString());
        return String.join(",", parts);
    }

    private static void addString(List<String> parts, String key, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(key + "=" + value);
        }
    }

    private static void addLong(List<String> parts, String key, long value) {
        if (value >= 0) {
            parts.add(key + "=" + value);
        }
    }
}
