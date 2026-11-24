package com.kset.image.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.kset.image.ImageException;
import com.kset.image.config.ImageProperties;
import com.kset.image.vision.VisionClient;
import com.kset.image.vision.VisionRequest;
import com.kset.image.vision.VisionResult;

/**
 * 基于 OpenAI 兼容 /chat/completions 的图片理解客户端，覆盖 DashScope 兼容模式、OpenAI 及私有化兼容服务。
 */
public class OpenAiCompatibleVisionClient implements VisionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleVisionClient.class);

    private final RestClient restClient;
    private final String model;
    private final int maxTokens;

    public OpenAiCompatibleVisionClient(String baseUrl, String apiKey, ImageProperties.Vision config) {
        Assert.notNull(config, "vision 配置不能为 null");
        Assert.hasText(config.getModel(), "vision.model 不能为空");
        Assert.isTrue(config.getMaxTokens() > 0, "vision.maxTokens 必须为正数");
        this.restClient = ImageClientSupport.createClient(baseUrl, apiKey, config.getTimeout());
        this.model = config.getModel();
        this.maxTokens = config.getMaxTokens();
    }

    @Override
    public VisionResult understand(VisionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为 null");
        }
        ObjectNode payload = ImageClientSupport.MAPPER.createObjectNode();
        payload.put("model", model);
        payload.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : maxTokens);

        ArrayNode content = payload.putArray("messages").addObject().put("role", "user").putArray("content");
        content.addObject().put("type", "text").put("text", buildPrompt(request));
        content.addObject().put("type", "image_url").putObject("image_url").put("url", resolveImageUrl(request));

        String body;
        try {
            body = restClient.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ImageClientSupport.MAPPER.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ImageException("[kset-image] 图片理解请求失败", e);
        } catch (IOException e) {
            throw new ImageException("[kset-image] 图片理解请求体序列化失败", e);
        }

        String rawText = extractContent(body);
        VisionResult result = new VisionResult(rawText, parseStructuredParams(request, rawText));
        log.info("[kset-image] 图片理解完成，model={}，输出长度={}", model, rawText.length());
        return result;
    }

    private String buildPrompt(VisionRequest request) {
        if (request.responseSchema() == null || request.responseSchema().isBlank()) {
            return request.prompt();
        }
        return request.prompt() + "\n只返回符合以下结构的 JSON，不要输出其他内容：\n" + request.responseSchema();
    }

    private String resolveImageUrl(VisionRequest request) {
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            return request.imageUrl();
        }
        if (request.imageBase64() != null && !request.imageBase64().isBlank()) {
            return ImageDataUrls.fromBase64(request.imageBase64());
        }
        return toDataUrl(request.imagePath());
    }

    static String toDataUrl(Path path) {
        try {
            return ImageDataUrls.fromBytes(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new ImageException("[kset-image] 读取本地图片失败", e);
        }
    }

    static String extractContent(String body) {
        JsonNode root = ImageClientSupport.readResponse(body, "图片理解");
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new ImageException("[kset-image] 图片理解响应缺少文本内容");
        }
        return content.asText();
    }

    static Map<String, Object> parseStructuredParams(VisionRequest request, String rawText) {
        if (request.responseSchema() == null || request.responseSchema().isBlank()) {
            return Map.of();
        }
        String json = rawText.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            Map<String, Object> params = ImageClientSupport.MAPPER.readValue(json, new TypeReference<Map<String, Object>>() { });
            return params == null ? Map.of() : params;
        } catch (IOException e) {
            log.warn("[kset-image] 结构化参数解析失败，仅返回原始文本");
            return Map.of();
        }
    }
}
