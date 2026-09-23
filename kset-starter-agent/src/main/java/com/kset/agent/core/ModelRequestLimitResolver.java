package com.kset.agent.core;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.regex.MatchResult;

/** 从模型异常中区分可压缩的 Token 限额和仅需退避的请求限流。 */
@Component
public class ModelRequestLimitResolver {

    private static final double FALLBACK_COMPRESSION_RATIO = 0.75d;
    private static final Pattern REQUESTED_PATTERN = Pattern.compile(
            "(?:requested|resulted in|request(?:ed)? tokens?)[^0-9]{0,32}([0-9][0-9,]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALLOWED_PATTERN = Pattern.compile(
            "(?:allowed|limit|max(?:imum)?(?: context length)?(?: is)?|supports? up to)[^0-9]{0,32}([0-9][0-9,]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern USED_PATTERN = Pattern.compile(
            "(?:used|usage)[^0-9]{0,32}([0-9][0-9,]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile(
            "retry[- ]after[^0-9]{0,16}([0-9]+)(?:\\s*(ms|millisecond|milliseconds|s|sec|second|seconds))?",
            Pattern.CASE_INSENSITIVE);

    public LimitDecision resolve(Throwable error) {
        String message = messages(error);
        String normalized = message.toLowerCase(Locale.ROOT);
        boolean tokenLimit = containsAny(normalized,
                "context_length", "context length", "context window", "maximum context",
                "too many tokens", "token limit", "tokens per min", "tokens per minute", "tpm")
                || (normalized.contains("requested") && normalized.contains("limit")
                && normalized.contains("token"));
        boolean requestRateLimit = containsAny(normalized,
                "requests per min", "requests per minute", "rpm", "concurrent", "concurrency",
                "too many requests", "rate limit", "status code 429", "429");
        if (tokenLimit) {
            Long requested = findNumber(REQUESTED_PATTERN, message);
            Long allowed = findNumber(ALLOWED_PATTERN, message);
            Long used = findNumber(USED_PATTERN, message);
            Long available = allowed != null && used != null
                    ? Math.max(0L, allowed - used) : allowed;
            double ratio = requested != null && available != null && requested > 0 && available > 0
                    ? Math.min(0.95d, Math.max(0.05d,
                    available.doubleValue() / requested.doubleValue() * 0.9d))
                    : requested != null && requested > 0 && available != null && available == 0L
                    ? 0.05d
                    : FALLBACK_COMPRESSION_RATIO;
            return new LimitDecision(LimitType.TOKEN, ratio, requested, available, null, message);
        }
        if (requestRateLimit) {
            return new LimitDecision(LimitType.REQUEST_RATE, 1.0d, null, null,
                    retryAfterMillis(error, message), message);
        }
        Integer status = httpStatus(error);
        boolean permanentClientError = status != null && (status == 400 || status == 401 || status == 403)
                || containsAny(normalized, "status 400", "status code 400",
                "status 401", "status code 401", "unauthorized",
                "status 403", "status code 403", "forbidden");
        if (permanentClientError) {
            return new LimitDecision(LimitType.PERMANENT, 1.0d, null, null, null, message);
        }
        boolean transientFailure = status != null && (status == 408 || status == 425
                || status == 500 || status == 502 || status == 503 || status == 504)
                || containsAny(normalized, "timeout", "timed out", "connection reset",
                "connection refused", "temporarily unavailable", "service unavailable",
                "bad gateway", "gateway timeout");
        if (transientFailure) {
            return new LimitDecision(LimitType.TRANSIENT, 1.0d, null, null,
                    retryAfterMillis(error, message), message);
        }
        return new LimitDecision(LimitType.UNKNOWN, 1.0d, null, null, null, message);
    }

    private Long findNumber(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String messages(Throwable error) {
        List<String> values = new ArrayList<>();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                values.add(current.getMessage());
            }
            String responseBody = responseBody(current);
            if (responseBody != null && !responseBody.isBlank()) {
                values.add(responseBody);
            }
            current = current.getCause();
        }
        return String.join(" | ", values);
    }

    private String responseBody(Throwable error) {
        try {
            Method method = error.getClass().getMethod("getResponseBodyAsString");
            Object value = method.invoke(error);
            return value != null ? String.valueOf(value) : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Integer httpStatus(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 12) {
            Integer status = invokeInt(current, "getRawStatusCode");
            if (status != null) {
                return status;
            }
            try {
                Method method = current.getClass().getMethod("getStatusCode");
                Object statusCode = method.invoke(current);
                if (statusCode != null) {
                    Integer value = invokeInt(statusCode, "value");
                    if (value != null) {
                        return value;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // 供应 SDK 未暴露统一状态接口时继续检查异常链。
            }
            current = current.getCause();
        }
        return null;
    }

    private Integer invokeInt(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Number number ? number.intValue() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Long retryAfterMillis(Throwable error, String message) {
        Long headerValue = retryAfterHeaderMillis(error);
        if (headerValue != null) {
            return headerValue;
        }
        return RETRY_AFTER_PATTERN.matcher(message).results().findFirst()
                .map(this::parseRetryAfterMatch)
                .orElse(null);
    }

    private Long retryAfterHeaderMillis(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 12) {
            try {
                Object headers = current.getClass().getMethod("getResponseHeaders").invoke(current);
                if (headers != null) {
                    Object value = headers.getClass().getMethod("getFirst", String.class)
                            .invoke(headers, "Retry-After");
                    if (value != null) {
                        return Duration.ofSeconds(Long.parseLong(String.valueOf(value).trim())).toMillis();
                    }
                }
            } catch (ReflectiveOperationException | NumberFormatException ignored) {
                // Retry-After 也可能是 HTTP 日期；无法稳定解析时使用本地退避。
            }
            current = current.getCause();
        }
        return null;
    }

    private Long parseRetryAfterMatch(MatchResult match) {
        try {
            long value = Long.parseLong(match.group(1));
            String unit = match.group(2);
            return unit != null && unit.toLowerCase(Locale.ROOT).startsWith("m")
                    ? value : Duration.ofSeconds(value).toMillis();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public enum LimitType {
        TOKEN,
        REQUEST_RATE,
        TRANSIENT,
        PERMANENT,
        UNKNOWN
    }

    public record LimitDecision(LimitType type, double compressionRatio,
                                Long requestedTokens, Long allowedTokens,
                                Long retryAfterMillis, String message) {
        public boolean compressible() {
            return type == LimitType.TOKEN;
        }

        public boolean retryable() {
            return type == LimitType.REQUEST_RATE || type == LimitType.TRANSIENT;
        }
    }
}
