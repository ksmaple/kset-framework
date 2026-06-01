package com.kset.cache.interceptor;

import com.kset.cache.core.KsetCacheFacade;
import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.config.KsetCacheProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Aspect
public class KsetCacheAspect {

    private final KsetCacheFacade cacheFacade;
    private final KsetCacheProperties properties;
    private final KsetCacheKeyEvaluator keyEvaluator;

    public KsetCacheAspect(KsetCacheFacade cacheFacade,
                           KsetCacheProperties properties,
                           KsetCacheKeyEvaluator keyEvaluator) {
        this.cacheFacade = cacheFacade;
        this.properties = properties;
        this.keyEvaluator = keyEvaluator;
    }

    @Around("@annotation(com.kset.cache.annotation.KsetCacheable)"
            + " || @annotation(com.kset.cache.annotation.KsetCachePut)"
            + " || @annotation(com.kset.cache.annotation.KsetCacheEvict)"
            + " || @annotation(com.kset.cache.annotation.KsetCaching)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method method = specificMethod(point);
        List<KsetCacheOperation> operations = KsetCacheOperationParser.parse(method);
        if (operations.isEmpty()) {
            operations = KsetCacheOperationParser.parse(((MethodSignature) point.getSignature()).getMethod());
        }
        if (operations.isEmpty()) {
            return point.proceed();
        }
        Object target = point.getTarget();
        Object[] args = point.getArgs();
        evict(operations, method, args, target, KsetCacheKeyEvaluator.noResult(), true);
        List<KsetCacheSpec> cacheableSpecs = operations.stream()
                .filter(operation -> operation.kind() == KsetCacheOperation.Kind.CACHEABLE)
                .map(operation -> toSpec(operation, method, args, target, KsetCacheKeyEvaluator.noResult(), method.getReturnType()))
                .toList();
        Object result;
        if (!cacheableSpecs.isEmpty()) {
            result = cacheFacade.getOrLoad(cacheableSpecs, () -> proceed(point));
        } else {
            result = point.proceed();
        }
        put(operations, method, args, target, result, method.getReturnType());
        evict(operations, method, args, target, result, false);
        return result;
    }

    private static Object proceed(ProceedingJoinPoint point) throws Exception {
        try {
            return point.proceed();
        } catch (Exception e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }

    private void put(List<KsetCacheOperation> operations, Method method, Object[] args, Object target, Object result, Class<?> type) {
        operations.stream()
                .filter(operation -> operation.kind() == KsetCacheOperation.Kind.PUT)
                .map(operation -> toSpec(operation, method, args, target, result, type))
                .forEach(spec -> cacheFacade.put(spec, result));
    }

    private void evict(List<KsetCacheOperation> operations,
                       Method method,
                       Object[] args,
                       Object target,
                       Object result,
                       boolean beforeInvocation) {
        operations.stream()
                .filter(operation -> operation.kind() == KsetCacheOperation.Kind.EVICT)
                .filter(operation -> operation.beforeInvocation() == beforeInvocation)
                .map(operation -> toSpec(operation, method, args, target, result, method.getReturnType()))
                .forEach(cacheFacade::evict);
    }

    private KsetCacheSpec toSpec(KsetCacheOperation operation,
                                 Method method,
                                 Object[] args,
                                 Object target,
                                 Object result,
                                 Class<?> valueType) {
        List<KsetCacheLayer> layers = operation.layers().isEmpty()
                ? properties.getDefaultLayers()
                : operation.layers();
        return new KsetCacheSpec(
                operation.cacheName(),
                keyEvaluator.evalKey(operation.key(), method, args, target, result),
                layers,
                parseDuration(operation.ttl(), null),
                parseDuration(operation.nullTtl(), properties.getNullTtl()),
                operation.cacheNull(),
                valueType);
    }

    private static Method specificMethod(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        return AopUtils.getMostSpecificMethod(method, Objects.requireNonNull(point.getTarget()).getClass());
    }

    static Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String text = value.trim().toLowerCase();
        try {
            if (text.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2)));
            }
            if (text.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            if (text.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            if (text.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            if (text.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(text.substring(0, text.length() - 1)));
            }
            return Duration.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid cache duration: " + value, e);
        }
    }
}
