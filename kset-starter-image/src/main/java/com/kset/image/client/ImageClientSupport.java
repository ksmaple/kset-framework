package com.kset.image.client;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.kset.image.ImageException;

/** 图片客户端共同使用的连接配置与响应边界，不暴露供应商业务规则。 */
final class ImageClientSupport {

    static final ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ImageClientSupport() {
    }

    static RestClient createClient(String baseUrl, String apiKey, Duration timeout) {
        Assert.hasText(baseUrl, "baseUrl 不能为空");
        Assert.hasText(apiKey, "apiKey 不能为空");
        URI uri = URI.create(baseUrl);
        Assert.isTrue(("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null, "baseUrl 必须为不含凭据、查询或片段的 HTTP(S) 地址");
        int timeoutMillis = timeoutMillis(timeout);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.strip()).build();
    }

    private static int timeoutMillis(Duration timeout) {
        Assert.notNull(timeout, "timeout 不能为 null");
        Assert.isTrue(timeout.compareTo(Duration.ofMillis(1)) >= 0
                && timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0,
                "timeout 必须在 1ms 至 Integer.MAX_VALUE ms 之间");
        return Math.toIntExact(timeout.toMillis());
    }

    static JsonNode readResponse(String body, String operation) {
        if (!StringUtils.hasText(body)) {
            throw new ImageException("[kset-image] " + operation + "响应为空");
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root == null || !root.isObject()) {
                throw new ImageException("[kset-image] " + operation + "响应必须为 JSON 对象");
            }
            return root;
        } catch (IOException e) {
            throw new ImageException("[kset-image] " + operation + "响应解析失败", e);
        }
    }

    static String imageValue(JsonNode value) {
        return value.isTextual() && StringUtils.hasText(value.textValue()) ? value.textValue() : null;
    }
}
