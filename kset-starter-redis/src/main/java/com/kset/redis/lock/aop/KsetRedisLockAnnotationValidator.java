package com.kset.redis.lock.aop;

import com.kset.redis.lock.annotation.KsetLocked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动后扫描 {@link KsetLocked}，对 AOP 无法织入的场景输出 WARN。
 */
public class KsetRedisLockAnnotationValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(KsetRedisLockAnnotationValidator.class);

    private static final String SELF_INVOKE_HINT = """
            @KsetLocked 依赖 Spring AOP 代理，同类方法内 this.xxx() 自调用不会加锁。\
            请改为：注入本类代理/接口、拆分到另一 @Service、或使用 KsetRedisLockExecutor。""";

    private final ApplicationContext applicationContext;
    private final boolean enabled;

    public KsetRedisLockAnnotationValidator(ApplicationContext applicationContext, boolean enabled) {
        this.applicationContext = applicationContext;
        this.enabled = enabled;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!enabled) {
            return;
        }
        List<String> issues = collectIssues();
        if (!issues.isEmpty()) {
            for (String issue : issues) {
                log.warn("[kset-redis-lock] {}", issue);
            }
        }
        if (!issues.isEmpty() || hasAnyKsetLockedBean()) {
            log.info("[kset-redis-lock] {}", SELF_INVOKE_HINT);
        }
    }

    List<String> collectIssues() {
        List<String> issues = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception ex) {
                continue;
            }
            Class<?> targetClass = resolveTargetClass(bean);
            boolean hasLockedMethod = false;
            for (Method method : targetClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(KsetLocked.class)) {
                    continue;
                }
                hasLockedMethod = true;
                String location = targetClass.getSimpleName() + "#" + method.getName();
                if (!Modifier.isPublic(method.getModifiers())) {
                    issues.add(location + ": 方法非 public，@KsetLocked 不会生效");
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    issues.add(location + ": static 方法无法使用 @KsetLocked");
                }
                if (Modifier.isFinal(method.getModifiers())) {
                    issues.add(location + ": final 方法可能无法被 CGLIB 增强");
                }
            }
            if (hasLockedMethod && Modifier.isFinal(targetClass.getModifiers())) {
                issues.add(targetClass.getName() + ": 类为 final，CGLIB 代理可能失败");
            }
        }
        return issues;
    }

    private boolean hasAnyKsetLockedBean() {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            try {
                Class<?> targetClass = resolveTargetClass(applicationContext.getBean(beanName));
                for (Method method : targetClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(KsetLocked.class)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        return false;
    }

    private static Class<?> resolveTargetClass(Object bean) {
        return ClassUtils.getUserClass(AopUtils.getTargetClass(bean));
    }
}
