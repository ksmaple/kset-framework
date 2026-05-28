package com.kset.monitor.aop;

import com.kset.monitor.Monitor;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;
import com.kset.monitor.facade.MonitorTypes;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * {@link Monitored} 注解切面。
 */
@Aspect
public class MonitorAspect {

    @Around("@annotation(monitored)")
    public Object around(ProceedingJoinPoint joinPoint, Monitored monitored) throws Throwable {
        String type = monitored.type();
        if (type == null || type.isBlank()) {
            type = MonitorTypes.BIZ;
        }
        String name = monitored.name();
        if (name == null || name.isBlank()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            name = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        }
        try (MonitorTransaction tx = Monitor.newTransaction(type, name)) {
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
}
