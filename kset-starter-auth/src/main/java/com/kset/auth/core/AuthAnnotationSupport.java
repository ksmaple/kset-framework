package com.kset.auth.core;

import com.kset.auth.annotation.SkipAuth;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

public final class AuthAnnotationSupport {

    private AuthAnnotationSupport() {
    }

    public static boolean shouldSkipAuth(Method method, Class<?> targetClass) {
        if (method != null && AnnotatedElementUtils.hasAnnotation(method, SkipAuth.class)) {
            return true;
        }
        return targetClass != null && AnnotatedElementUtils.hasAnnotation(targetClass, SkipAuth.class);
    }
}
