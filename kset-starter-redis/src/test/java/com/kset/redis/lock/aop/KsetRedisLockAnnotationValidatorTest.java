package com.kset.redis.lock.aop;

import com.kset.redis.lock.annotation.KsetLocked;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KsetRedisLockAnnotationValidatorTest {

    @Test
    void ignoresFinalJdkProxyWithoutLockedMethod() {
        Runnable proxy = (Runnable) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Runnable.class},
                (target, method, args) -> null);
        KsetRedisLockAnnotationValidator validator = new KsetRedisLockAnnotationValidator(contextWithBean(proxy), true);

        List<String> issues = validator.collectIssues();

        assertEquals(List.of(), issues);
    }

    @Test
    void reportsFinalClassOnlyWhenLockedMethodExists() {
        KsetRedisLockAnnotationValidator validator =
                new KsetRedisLockAnnotationValidator(contextWithBean(new FinalLockedService()), true);

        List<String> issues = validator.collectIssues();

        assertTrue(issues.contains(FinalLockedService.class.getName() + ": 类为 final，CGLIB 代理可能失败"));
    }

    private static ApplicationContext contextWithBean(Object bean) {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{"sampleBean"});
        when(context.getBean("sampleBean")).thenReturn(bean);
        return context;
    }

    private static final class FinalLockedService {

        @KsetLocked("'sample'")
        public void sync() {
        }
    }
}
