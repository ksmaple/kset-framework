package com.kset.redis.lock.aop;

import com.kset.redis.lock.annotation.KsetLocked;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KsetRedisLockKeyResolverTest {

    private final KsetRedisLockKeyResolver resolver = new KsetRedisLockKeyResolver();

    @Test
    void resolvesSpelFromMethodArgs() throws Exception {
        Method method = Sample.class.getDeclaredMethod("sync", Long.class);
        KsetLocked locked = method.getAnnotation(KsetLocked.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});

        List<String> keys = resolver.resolve(joinPoint, locked);

        assertEquals(List.of("order:42"), keys);
    }

  @SuppressWarnings("unused")
    private static final class Sample {
        @KsetLocked("'order:' + #orderId")
        void sync(Long orderId) {
        }
    }
}
