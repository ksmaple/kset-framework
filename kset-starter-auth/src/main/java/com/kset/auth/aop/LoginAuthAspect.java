package com.kset.auth.aop;

import com.kset.auth.annotation.RequireLogin;
import com.kset.auth.annotation.RequirePermission;
import com.kset.auth.annotation.RequireRole;
import com.kset.auth.core.AuthAnnotationSupport;
import com.kset.auth.spi.PermissionChecker;
import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginUser;
import com.kset.common.auth.PermissionDeniedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
public class LoginAuthAspect {

    private final PermissionChecker permissionChecker;

    public LoginAuthAspect(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Around("@annotation(requireLogin)")
    public Object requireLogin(ProceedingJoinPoint joinPoint, RequireLogin requireLogin) throws Throwable {
        if (shouldSkipAuth(joinPoint)) {
            return joinPoint.proceed();
        }
        LoginContext.requireUser(requireLogin.subject());
        return joinPoint.proceed();
    }

    @Around("@annotation(requireRole)")
    public Object requireRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        if (shouldSkipAuth(joinPoint)) {
            return joinPoint.proceed();
        }
        LoginUser user = LoginContext.requireUser(requireRole.subject());
        boolean allowed = match(requireRole.value(), requireRole.matchAll(),
                value -> permissionChecker.hasRole(user, requireRole.subject(), value));
        if (!allowed) {
            throw new PermissionDeniedException("无权限");
        }
        return joinPoint.proceed();
    }

    @Around("@annotation(requirePermission)")
    public Object requirePermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        if (shouldSkipAuth(joinPoint)) {
            return joinPoint.proceed();
        }
        LoginUser user = LoginContext.requireUser(requirePermission.subject());
        boolean allowed = match(requirePermission.value(), requirePermission.matchAll(),
                value -> permissionChecker.hasPermission(user, requirePermission.subject(), value));
        if (!allowed) {
            throw new PermissionDeniedException("无权限");
        }
        return joinPoint.proceed();
    }

    private boolean match(String[] values, boolean matchAll, Checker checker) {
        if (values == null || values.length == 0) {
            return true;
        }
        return matchAll
                ? Arrays.stream(values).allMatch(checker::has)
                : Arrays.stream(values).anyMatch(checker::has);
    }

    private boolean shouldSkipAuth(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return false;
        }
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null ? joinPoint.getTarget().getClass() : method.getDeclaringClass();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        return AuthAnnotationSupport.shouldSkipAuth(specificMethod, targetClass);
    }

    private interface Checker {
        boolean has(String value);
    }
}
