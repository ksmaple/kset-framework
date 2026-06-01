package com.kset.cache.interceptor;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class KsetCacheKeyEvaluator {

    private static final Object NO_RESULT = new Object();
    private static final String DEFAULT_KEY_GENERATOR = "ksetDefaultCacheKeyGenerator";

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, KsetCacheKeyGenerator> keyGenerators;

    public KsetCacheKeyEvaluator() {
        this(Map.of());
    }

    public KsetCacheKeyEvaluator(Map<String, KsetCacheKeyGenerator> keyGenerators) {
        this.keyGenerators = new HashMap<>(keyGenerators != null ? keyGenerators : Map.of());
        this.keyGenerators.putIfAbsent(DEFAULT_KEY_GENERATOR, new DefaultKsetCacheKeyGenerator());
    }

    public String evalKey(String expression, Method method, Object[] args, Object target, Object result) {
        return evalKey(expression, "", method, args, target, result);
    }

    public String evalKey(String expression,
                          String keyGenerator,
                          Method method,
                          Object[] args,
                          Object target,
                          Object result) {
        if (expression == null || expression.isBlank()) {
            return generateKey(keyGenerator, target, method, args);
        }
        Object value = parser.parseExpression(expression)
                .getValue(context(method, args, target, result));
        if (value == null) {
            throw new IllegalArgumentException("cache key expression returned null: " + expression);
        }
        return String.valueOf(value);
    }

    public boolean evalCondition(String expression, Method method, Object[] args, Object target, Object result, boolean defaultValue) {
        if (expression == null || expression.isBlank()) {
            return defaultValue;
        }
        Boolean value = parser.parseExpression(expression)
                .getValue(context(method, args, target, result), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    private MethodBasedEvaluationContext context(Method method, Object[] args, Object target, Object result) {
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                new KsetCacheExpressionRoot(target, method),
                method,
                args != null ? args : new Object[0],
                parameterNameDiscoverer);
        if (result != NO_RESULT) {
            context.setVariable("result", result);
        }
        return context;
    }

    public static Object noResult() {
        return NO_RESULT;
    }

    private String generateKey(String keyGenerator, Object target, Method method, Object[] args) {
        String generatorName = keyGenerator == null || keyGenerator.isBlank()
                ? DEFAULT_KEY_GENERATOR
                : keyGenerator;
        KsetCacheKeyGenerator generator = keyGenerators.get(generatorName);
        if (generator == null) {
            throw new IllegalArgumentException("KsetCacheKeyGenerator bean not found: " + generatorName);
        }
        String key = generator.generate(target, method, args);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("KsetCacheKeyGenerator returned blank key: " + generatorName);
        }
        return key;
    }
}
