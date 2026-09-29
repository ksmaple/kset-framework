package com.kset.agent.core;

import com.kset.agent.core.ModelRequestLimitResolver.LimitDecision;
import com.kset.agent.core.ModelRequestLimitResolver.LimitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRequestLimitResolverTest {

    private final ModelRequestLimitResolver resolver = new ModelRequestLimitResolver();

    @Test
    void tokenLimitWithTokenNumbersComputesCompressionRatio() {
        LimitDecision decision = resolver.resolve(new RuntimeException(
                "This model's maximum context length is 4096 tokens. However, you requested 5000 tokens."));
        assertEquals(LimitType.TOKEN, decision.type());
        assertTrue(decision.compressible());
        assertFalse(decision.retryable());
        assertEquals(5000L, decision.requestedTokens());
        assertEquals(4096L, decision.allowedTokens());
        assertEquals(4096.0d / 5000.0d * 0.9d, decision.compressionRatio(), 1e-9);
        assertNull(decision.retryAfterMillis());
    }

    @Test
    void tokenLimitWithOnlyAllowedNumberUsesFallbackRatio() {
        LimitDecision decision = resolver.resolve(new RuntimeException("maximum context length is 8192 tokens"));
        assertEquals(LimitType.TOKEN, decision.type());
        assertEquals(0.75d, decision.compressionRatio(), 1e-9);
        assertNull(decision.requestedTokens());
        assertEquals(8192L, decision.allowedTokens());
    }

    @Test
    void tokenLimitWithoutAllowedNumberFallsBackToDefaultRatio() {
        LimitDecision decision = resolver.resolve(new RuntimeException("Request failed: too many tokens"));
        assertEquals(LimitType.TOKEN, decision.type());
        assertTrue(decision.compressible());
        assertEquals(0.75d, decision.compressionRatio());
        assertNull(decision.allowedTokens());
    }

    @Test
    void requestRateLimitParsesRetryAfterSeconds() {
        LimitDecision decision = resolver.resolve(new RuntimeException("Too many requests. Retry-After: 3"));
        assertEquals(LimitType.REQUEST_RATE, decision.type());
        assertTrue(decision.retryable());
        assertFalse(decision.compressible());
        assertEquals(3000L, decision.retryAfterMillis());
    }

    @Test
    void requestRateLimitParsesRetryAfterMilliseconds() {
        LimitDecision decision = resolver.resolve(new RuntimeException("rate limit exceeded, retry after 250 ms"));
        assertEquals(LimitType.REQUEST_RATE, decision.type());
        assertEquals(250L, decision.retryAfterMillis());
    }

    @Test
    void statusCode429IsRequestRateLimit() {
        LimitDecision decision = resolver.resolve(new RuntimeException("HTTP 429 too many requests"));
        assertEquals(LimitType.REQUEST_RATE, decision.type());
        assertNull(decision.retryAfterMillis());
    }

    @Test
    void unauthorizedIsPermanent() {
        LimitDecision decision = resolver.resolve(new RuntimeException("status code 401: Unauthorized"));
        assertEquals(LimitType.PERMANENT, decision.type());
        assertFalse(decision.retryable());
        assertFalse(decision.compressible());
        assertNull(decision.retryAfterMillis());
    }

    @Test
    void forbiddenIsPermanent() {
        LimitDecision decision = resolver.resolve(new RuntimeException("Access denied: 403 Forbidden"));
        assertEquals(LimitType.PERMANENT, decision.type());
    }

    @Test
    void timeoutIsTransient() {
        LimitDecision decision = resolver.resolve(new RuntimeException("Connection timed out while calling model"));
        assertEquals(LimitType.TRANSIENT, decision.type());
        assertTrue(decision.retryable());
        assertNull(decision.retryAfterMillis());
    }

    @Test
    void serviceUnavailableIsTransient() {
        LimitDecision decision = resolver.resolve(new RuntimeException("upstream service unavailable"));
        assertEquals(LimitType.TRANSIENT, decision.type());
    }

    @Test
    void unrecognizedErrorIsUnknown() {
        LimitDecision decision = resolver.resolve(new RuntimeException("Null pointer in model adapter"));
        assertEquals(LimitType.UNKNOWN, decision.type());
        assertFalse(decision.retryable());
        assertFalse(decision.compressible());
        assertEquals(1.0d, decision.compressionRatio(), 1e-9);
    }

    @Test
    void messagesFromCauseChainAreInspected() {
        RuntimeException error = new RuntimeException("outer wrapper",
                new IllegalStateException("status code 429"));
        LimitDecision decision = resolver.resolve(error);
        assertEquals(LimitType.REQUEST_RATE, decision.type());
        assertTrue(decision.message().contains("outer wrapper"));
        assertTrue(decision.message().contains("status code 429"));
    }
}
