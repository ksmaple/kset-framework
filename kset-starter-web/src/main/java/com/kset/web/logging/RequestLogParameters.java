package com.kset.web.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kset.common.logging.LogMaskingUtil;
import com.kset.common.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.pattern.PathPattern;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Parsed request parameters for one bounded, redacted log entry. */
public final class RequestLogParameters {

    public static final int MAX_BYTES = 64 * 1024;

    private RequestLogParameters() {
    }

    public static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String value && !value.isBlank()) {
            return value;
        }
        if (pattern instanceof PathPattern value) {
            return value.getPatternString();
        }
        String path = LogMaskingUtil.maskFieldValue("path", request.getRequestURI());
        return path != null && path.length() > 256 ? path.substring(0, 256) + "..." : path;
    }

    public static String capture(HttpServletRequest request) {
        try {
            Budget budget = new Budget();
            ObjectNode parameters = JsonUtil.mapper().createObjectNode();
            appendPathVariables(request, parameters.putObject("path"), budget);
            appendServletParameters(request, parameters.putObject("queryOrForm"), budget);
            appendJsonBody(request, parameters, budget);
            String json = JsonUtil.mapper().writeValueAsString(parameters);
            String limited = limitUtf8(json, MAX_BYTES);
            if (limited.length() < json.length()) {
                budget.truncated = true;
            }
            return "parameters=" + limited + " parametersTruncated=" + budget.truncated;
        } catch (IOException | RuntimeException ignored) {
            return "parameters=unavailable parametersTruncated=unknown";
        }
    }

    private static void appendPathVariables(HttpServletRequest request, ObjectNode path, Budget budget) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> values)) {
            return;
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof String value)) {
                continue;
            }
            String masked = LogMaskingUtil.maskFieldValue(name, value);
            if (!budget.reserve(name, masked)) {
                path.put(name, "[TRUNCATED]");
                return;
            }
            path.put(name, masked);
        }
    }

    private static void appendServletParameters(HttpServletRequest request, ObjectNode servlet, Budget budget) {
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String name = entry.getKey();
            if (name == null || !budget.reserve(name, "")) {
                budget.truncated = true;
                return;
            }
            String[] values = entry.getValue();
            if (values == null) {
                servlet.putNull(name);
                continue;
            }
            ArrayNode array = servlet.putArray(name);
            for (String value : values) {
                String masked = LogMaskingUtil.maskFieldValue(name, value);
                if (!budget.reserve("", masked)) {
                    array.add("[TRUNCATED]");
                    return;
                }
                array.add(masked);
            }
        }
    }

    private static void appendJsonBody(HttpServletRequest request, ObjectNode parameters, Budget budget)
            throws IOException {
        if (!(request instanceof ContentCachingRequestWrapper cached)
                || request.getContentType() == null
                || !request.getContentType().toLowerCase(java.util.Locale.ROOT).contains("json")) {
            return;
        }
        byte[] body = cached.getContentAsByteArray();
        long contentLength = request.getContentLengthLong();
        if (body.length == 0) {
            if (contentLength > 0) {
                parameters.put("json", "[NOT_READ]");
                budget.truncated = true;
            }
            return;
        }
        if (body.length >= MAX_BYTES || contentLength > MAX_BYTES || budget.truncated) {
            parameters.put("json", "[TRUNCATED]");
            budget.truncated = true;
            return;
        }
        if (contentLength > body.length) {
            parameters.put("json", "[PARTIALLY_READ]");
            budget.truncated = true;
            return;
        }
        try {
            JsonNode parsed = JsonUtil.mapper().readTree(body);
            if (parsed == null) {
                return;
            }
            String masked = LogMaskingUtil.maskJson(JsonUtil.mapper().writeValueAsString(parsed));
            parameters.set("json", JsonUtil.mapper().readTree(masked));
        } catch (IOException | RuntimeException ignored) {
            parameters.put("json", "[UNPARSEABLE]");
        }
    }

    private static String limitUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        int index = 0;
        int bytes = 0;
        while (index < value.length()) {
            int point = value.codePointAt(index);
            int width = point <= 0x7F ? 1 : point <= 0x7FF ? 2 : point <= 0xFFFF ? 3 : 4;
            if (bytes + width > maxBytes - 3) {
                break;
            }
            bytes += width;
            index += Character.charCount(point);
        }
        return value.substring(0, index) + "...";
    }

    private static final class Budget {
        private int remaining = MAX_BYTES;
        private boolean truncated;

        private boolean reserve(String name, String value) {
            int bytes = name.getBytes(StandardCharsets.UTF_8).length
                    + (value != null ? value.getBytes(StandardCharsets.UTF_8).length : 0) + 8;
            if (bytes > remaining) {
                truncated = true;
                return false;
            }
            remaining -= bytes;
            return true;
        }
    }
}
