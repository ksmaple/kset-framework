package com.kset.schedule;

import com.kset.schedule.aop.KsetTaskLockAspect;
import com.kset.schedule.annotation.KsetTaskLock;
import com.kset.schedule.config.KsetScheduleProperties;
import com.kset.schedule.lock.TaskLockProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KsetTaskLockAspectTest {

    private final TaskLockProvider lockProvider = mock(TaskLockProvider.class);
    private final KsetTaskLockAspect aspect = new KsetTaskLockAspect(lockProvider, new KsetScheduleProperties());

    @Test
    void proceedsWhenLockAcquiredAndUnlocksAfterwards() throws Throwable {
        when(lockProvider.tryLock(anyString(), any())).thenReturn(true);
        AtomicInteger executions = new AtomicInteger();
        ProceedingJoinPoint joinPoint = mockJoinPoint();
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            executions.incrementAndGet();
            return "done";
        });

        Object result = aspect.around(joinPoint, annotation("", "5m", "1m"));

        assertThat(result).isEqualTo("done");
        assertThat(executions.get()).isEqualTo(1);
        verify(lockProvider).tryLock("SampleTask.run", Duration.ofMinutes(5));
        verify(lockProvider).unlock("SampleTask.run", Duration.ofMinutes(1));
    }

    @Test
    void skipsWhenLockHeldByOtherInstance() throws Throwable {
        when(lockProvider.tryLock(anyString(), any())).thenReturn(false);
        ProceedingJoinPoint joinPoint = mockJoinPoint();

        Object result = aspect.around(joinPoint, annotation("dbBackup", "", "0s"));

        assertThat(result).isNull();
        verify(joinPoint, never()).proceed();
        verify(lockProvider).tryLock(eq("dbBackup"), eq(Duration.ofMinutes(10)));
        verify(lockProvider, never()).unlock(anyString(), any());
    }

    @Test
    void unlockFailureDoesNotPropagate() throws Throwable {
        when(lockProvider.tryLock(anyString(), any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(lockProvider).unlock(anyString(), any());
        ProceedingJoinPoint joinPoint = mockJoinPoint();
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, annotation("", "", "0s"));

        assertThat(result).isEqualTo("ok");
    }

    private ProceedingJoinPoint mockJoinPoint() {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getDeclaringType()).thenReturn((Class) SampleTask.class);
        when(signature.getName()).thenReturn("run");
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    private static KsetTaskLock annotation(String name, String atMostFor, String atLeastFor) {
        return new KsetTaskLock() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return KsetTaskLock.class;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String atMostFor() {
                return atMostFor;
            }

            @Override
            public String atLeastFor() {
                return atLeastFor;
            }
        };
    }

    static class SampleTask {
        void run() {
        }
    }
}
