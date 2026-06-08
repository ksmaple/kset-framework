package com.kset.auth.core;

import com.kset.auth.annotation.SkipAuth;
import com.kset.auth.annotation.SkipAuthScheme;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class AuthAnnotationSupport {

    private AuthAnnotationSupport() {
    }

    public static boolean shouldSkipAuth(Method method, Class<?> targetClass) {
        if (method != null && AnnotatedElementUtils.hasAnnotation(method, SkipAuth.class)) {
            return true;
        }
        return targetClass != null && AnnotatedElementUtils.hasAnnotation(targetClass, SkipAuth.class);
    }

    public static boolean shouldSkipLogin(Method method, Class<?> targetClass) {
        return shouldSkipAuth(method, targetClass)
                || shouldSkipScheme(method, targetClass, AuthSchemes.SESSION)
                || shouldSkipScheme(method, targetClass, AuthSchemes.TRUSTED_HEADER);
    }

    public static boolean shouldSkipScheme(Method method, Class<?> targetClass, String scheme) {
        if (!hasText(scheme)) {
            return false;
        }
        return hasScheme(method, scheme) || hasScheme(targetClass, scheme);
    }

    private static boolean hasScheme(AnnotatedElement element, String scheme) {
        if (element == null) {
            return false;
        }
        SkipAuthScheme annotation = AnnotatedElementUtils.findMergedAnnotation(element, SkipAuthScheme.class);
        if (annotation == null || annotation.value().length == 0) {
            return false;
        }
        String expected = normalize(scheme);
        return Arrays.stream(annotation.value())
                .filter(AuthAnnotationSupport::hasText)
                .map(AuthAnnotationSupport::normalize)
                .anyMatch(expected::equals);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
