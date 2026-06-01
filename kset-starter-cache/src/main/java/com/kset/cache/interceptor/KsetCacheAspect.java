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
import java.util.Collection;
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
                .filter(operation -> matchesCondition(operation, method, args, target, KsetCacheKeyEvaluator.noResult()))
                .filter(operation -> operation.unless().isBlank())
                .map(operation -> toSpecs(operation, method, args, target, KsetCacheKeyEvaluator.noResult(), method.getReturnType()))
                .flatMap(Collection::stream)
                .toList();
        List<KsetCacheOperation> deferredCacheables = operations.stream()
                .filter(operation -> operation.kind() == KsetCacheOperation.Kind.CACHEABLE)
                .filter(operation -> matchesCondition(operation, method, args, target, KsetCacheKeyEvaluator.noResult()))
                .filter(operation -> !operation.unless().isBlank())
                .toList();
        Object result;
        if (deferredCacheables.isEmpty() && !cacheableSpecs.isEmpty()) {
            result = cacheFacade.getOrLoad(cacheableSpecs, () -> proceed(point));
        } else if (!cacheableSpecs.isEmpty() || !deferredCacheables.isEmpty()) {
            result = cacheFacade.getOrLoad(
                    readSpecs(cacheableSpecs, deferredCacheables, method, args, target),
                    () -> proceed(point),
                    value -> writeSpecs(cacheableSpecs, deferredCacheables, method, args, target, value));
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
                .filter(operation -> matchesCondition(operation, method, args, target, result))
                .filter(operation -> !matchesUnless(operation, method, args, target, result))
                .map(operation -> toSpecs(operation, method, args, target, result, type))
                .flatMap(Collection::stream)
                .forEach(spec -> cacheFacade.put(spec, result));
    }

    private List<KsetCacheSpec> readSpecs(List<KsetCacheSpec> directSpecs,
                                          List<KsetCacheOperation> deferredOperations,
                                          Method method,
                                          Object[] args,
                                          Object target) {
        List<KsetCacheSpec> allReadSpecs = new java.util.ArrayList<>(directSpecs);
        deferredOperations.stream()
                .map(operation -> toSpecs(operation, method, args, target, KsetCacheKeyEvaluator.noResult(), method.getReturnType()))
                .forEach(allReadSpecs::addAll);
        return allReadSpecs;
    }

    private List<KsetCacheSpec> writeSpecs(List<KsetCacheSpec> directSpecs,
                                           List<KsetCacheOperation> deferredOperations,
                                           Method method,
                                           Object[] args,
                                           Object target,
                                           Object loaded) {
        List<KsetCacheSpec> writeSpecs = new java.util.ArrayList<>(directSpecs);
        deferredOperations.stream()
                .filter(operation -> !matchesUnless(operation, method, args, target, loaded))
                .map(operation -> toSpecs(operation, method, args, target, loaded, method.getReturnType()))
                .flatMap(Collection::stream)
                .forEach(writeSpecs::add);
        return writeSpecs;
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
                .filter(operation -> matchesCondition(operation, method, args, target, result))
                .forEach(operation -> evict(operation, method, args, target, result));
    }

    private void evict(KsetCacheOperation operation,
                       Method method,
                       Object[] args,
                       Object target,
                       Object result) {
        if (operation.allEntries()) {
            List<KsetCacheLayer> layers = operation.layers().isEmpty()
                    ? properties.getDefaultLayers()
                    : operation.layers();
            operation.cacheNames().forEach(cacheName -> cacheFacade.clear(cacheName, layers));
            return;
        }
        toSpecs(operation, method, args, target, result, method.getReturnType()).forEach(cacheFacade::evict);
    }

    private List<KsetCacheSpec> toSpecs(KsetCacheOperation operation,
                                        Method method,
                                        Object[] args,
                                        Object target,
                                        Object result,
                                        Class<?> valueType) {
        List<KsetCacheLayer> layers = operation.layers().isEmpty()
                ? properties.getDefaultLayers()
                : operation.layers();
        String key = keyEvaluator.evalKey(operation.key(), operation.keyGenerator(), method, args, target, result);
        Duration ttl = parseDuration(operation.ttl(), null);
        Duration nullTtl = parseDuration(operation.nullTtl(), properties.getNullTtl());
        return operation.cacheNames().stream()
                .map(cacheName -> new KsetCacheSpec(cacheName, key, layers, ttl, nullTtl, operation.cacheNull(), valueType))
                .toList();
    }

    private boolean matchesCondition(KsetCacheOperation operation,
                                     Method method,
                                     Object[] args,
                                     Object target,
                                     Object result) {
        return keyEvaluator.evalCondition(operation.condition(), method, args, target, result, true);
    }

    private boolean matchesUnless(KsetCacheOperation operation,
                                  Method method,
                                  Object[] args,
                                  Object target,
                                  Object result) {
        return keyEvaluator.evalCondition(operation.unless(), method, args, target, result, false);
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
