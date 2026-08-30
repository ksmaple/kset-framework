package com.kset.schedule.config;

import com.kset.schedule.annotation.KsetTaskLock;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/**
 * 启动期校验 {@link KsetTaskLock} 注解的时长格式：非法值在启动时 fail-fast，
 * 而不是等任务每次触发时才抛解析异常。
 */
public class KsetTaskLockAnnotationValidator implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;

    public KsetTaskLockAnnotationValidator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> targetClass = AopUtils.getTargetClass(applicationContext.getBean(beanName));
            if (targetClass == null) {
                continue;
            }
            for (Method method : targetClass.getDeclaredMethods()) {
                validate(beanName, targetClass, method);
            }
        }
    }

    private static void validate(String beanName, Class<?> targetClass, Method method) {
        KsetTaskLock annotation = AnnotatedElementUtils.findMergedAnnotation(method, KsetTaskLock.class);
        if (annotation == null) {
            return;
        }
        requireParseable(annotation.atMostFor(), "atMostFor", beanName, targetClass, method);
        requireParseable(annotation.atLeastFor(), "atLeastFor", beanName, targetClass, method);
    }

    private static void requireParseable(String value, String attribute,
                                         String beanName, Class<?> targetClass, Method method) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            org.springframework.boot.convert.DurationStyle.detectAndParse(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(String.format(
                    "@KsetTaskLock %s 时长格式非法: bean=%s, method=%s#%s, value='%s'",
                    attribute, beanName, targetClass.getSimpleName(), method.getName(), value), e);
        }
    }
}
