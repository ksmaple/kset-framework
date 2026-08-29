package com.kset.schedule.aop;

import com.kset.schedule.annotation.KsetTaskLock;
import com.kset.schedule.config.KsetScheduleProperties;
import com.kset.schedule.lock.TaskLockProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;

/**
 * {@link KsetTaskLock} 切面：抢到唯一运行锁才执行方法，抢不到跳过并返回 null。
 */
@Aspect
public class KsetTaskLockAspect {

    private static final Logger log = LoggerFactory.getLogger(KsetTaskLockAspect.class);

    private final TaskLockProvider lockProvider;
    private final KsetScheduleProperties properties;

    public KsetTaskLockAspect(TaskLockProvider lockProvider, KsetScheduleProperties properties) {
        this.lockProvider = lockProvider;
        this.properties = properties;
    }

    @Around("@annotation(ksetTaskLock)")
    public Object around(ProceedingJoinPoint joinPoint, KsetTaskLock ksetTaskLock) throws Throwable {
        String lockName = resolveLockName(joinPoint, ksetTaskLock);
        Duration atMostFor = parseDuration(ksetTaskLock.atMostFor(), properties.getLock().getAtMostFor());
        Duration atLeastFor = parseDuration(ksetTaskLock.atLeastFor(), Duration.ZERO);
        if (!lockProvider.tryLock(lockName, atMostFor)) {
            log.debug("[kset-schedule] 任务 {} 由其他实例执行，本实例跳过", lockName);
            return null;
        }
        try {
            return joinPoint.proceed();
        } finally {
            try {
                lockProvider.unlock(lockName, atLeastFor);
            } catch (RuntimeException e) {
                log.warn("[kset-schedule] 任务 {} 释放锁失败，等待 atMostFor 到期自动释放", lockName, e);
            }
        }
    }

    private static String resolveLockName(ProceedingJoinPoint joinPoint, KsetTaskLock ksetTaskLock) {
        if (ksetTaskLock.name() != null && !ksetTaskLock.name().isBlank()) {
            return ksetTaskLock.name().trim();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    private static Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return DurationStyle.detectAndParse(value.trim());
    }
}
