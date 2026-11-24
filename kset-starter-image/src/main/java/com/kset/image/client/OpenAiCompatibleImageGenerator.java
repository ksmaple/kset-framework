package com.kset.image.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.kset.image.ImageException;
import com.kset.image.config.ImageProperties;
import com.kset.image.generation.GeneratedImage;
import com.kset.image.generation.ImageGenerationRequest;
import com.kset.image.generation.ImageGenerationResult;
import com.kset.image.generation.ImageGenerator;

/**
 * OpenAI JSON 图片接口：文生图使用 /images/generations，参考图编辑使用 /images/edits。
 * 兼容服务须实现对应协议；不适用于万相原生异步接口。
 */
public class OpenAiCompatibleImageGenerator implements ImageGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleImageGenerator.class);

    private final RestClient restClient;
    private final String model;
    private final String defaultSize;

    public OpenAiCompatibleImageGenerator(String baseUrl, String apiKey, ImageProperties.Generation config) {
        Assert.notNull(config, "generation 配置不能为 null");
        Assert.hasText(config.getModel(), "generation.model 不能为空");
        Assert.hasText(config.getDefaultSize(), "generation.defaultSize 不能为空");
        this.restClient = ImageClientSupport.createClient(baseUrl, apiKey, config.getTimeout());
        this.model = config.getModel();
        this.defaultSize = config.getDefaultSize();
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为 null");
        }
        ObjectNode payload = ImageClientSupport.MAPPER.createObjectNode();
        payload.put("model", model);
        payload.put("prompt", request.prompt());
        payload.put("size", request.size() != null && !request.size().isBlank() ? request.size() : defaultSize);
        payload.put("n", request.count() != null ? request.count() : 1);
        boolean editing = request.referenceImageUrl() != null;
        if (editing) {
            payload.putArray("images").addObject().put("image_url", request.referenceImageUrl());
        }

        String body;
        try {
            body = restClient.post().uri(editing ? "/images/edits" : "/images/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ImageClientSupport.MAPPER.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ImageException("[kset-image] 图片生成请求失败", e);
        } catch (IOException e) {
            throw new ImageException("[kset-image] 图片生成请求体序列化失败", e);
        }

        ImageGenerationResult result = parseResult(body);
        log.info("[kset-image] 图片生成完成，model={}，数量={}", model, result.images().size());
        return result;
    }

    static ImageGenerationResult parseResult(String body) {
        JsonNode data = ImageClientSupport.readResponse(body, "图片生成").path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new ImageException("[kset-image] 图片生成响应缺少图片数据");
        }
        List<GeneratedImage> images = new ArrayList<>();
        for (JsonNode item : data) {
            String url = ImageClientSupport.imageValue(item.path("url"));
            String b64 = ImageClientSupport.imageValue(item.path("b64_json"));
            if (url == null && b64 == null) {
                continue;
            }
            images.add(new GeneratedImage(url, b64));
        }
        if (images.isEmpty()) {
            throw new ImageException("[kset-image] 图片生成响应无可用图片");
        }
        return new ImageGenerationResult(images);
    }
}
