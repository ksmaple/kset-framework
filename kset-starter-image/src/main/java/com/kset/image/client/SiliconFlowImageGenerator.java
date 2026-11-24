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

/** SiliconFlow 原生 JSON 图片协议，文生图与参考图均使用 /images/generations。 */
public class SiliconFlowImageGenerator implements ImageGenerator {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowImageGenerator.class);

    private final RestClient restClient;
    private final String model;
    private final String defaultSize;

    public SiliconFlowImageGenerator(String baseUrl, String apiKey, ImageProperties.Generation config) {
        Assert.notNull(config, "generation 配置不能为 null");
        Assert.hasText(config.getModel(), "generation.model 不能为空");
        Assert.hasText(config.getDefaultSize(), "generation.defaultSize 不能为空");
        this.restClient = ImageClientSupport.createClient(baseUrl, apiKey, config.getTimeout());
        this.model = config.getModel();
        this.defaultSize = config.getDefaultSize();
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        Assert.notNull(request, "request 不能为 null");
        Assert.isTrue(request.count() == null || request.count() == 1, "SiliconFlow 每次请求仅支持生成一张图片");
        ObjectNode payload = buildPayload(request);
        String body;
        try {
            body = restClient.post().uri("/images/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ImageClientSupport.MAPPER.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ImageException("[kset-image] SiliconFlow 图片生成请求失败", e);
        } catch (IOException e) {
            throw new ImageException("[kset-image] SiliconFlow 图片生成请求体序列化失败", e);
        }
        ImageGenerationResult result = parseResult(body);
        int count = result.images().size();
        log.info("[kset-image] 图片生成完成，provider=SILICONFLOW，model={}，数量={}", model, count);
        return result;
    }

    private ObjectNode buildPayload(ImageGenerationRequest request) {
        ObjectNode payload = ImageClientSupport.MAPPER.createObjectNode();
        payload.put("model", model);
        payload.put("prompt", request.prompt());
        if (request.referenceImageUrl() != null) {
            // Qwen Image Edit 不接受 image_size，参考图尺寸由供应商处理。
            Assert.isTrue(request.size() == null || request.size().isBlank(), "SiliconFlow 参考图请求不支持指定 size");
            payload.put("image", request.referenceImageUrl());
        } else {
            payload.put("image_size", request.size() != null && !request.size().isBlank() ? request.size() : defaultSize);
        }
        return payload;
    }

    static ImageGenerationResult parseResult(String body) {
        JsonNode data = ImageClientSupport.readResponse(body, "SiliconFlow 图片生成").path("images");
        if (!data.isArray() || data.isEmpty()) {
            throw new ImageException("[kset-image] SiliconFlow 图片生成响应缺少图片数据");
        }
        List<GeneratedImage> images = new ArrayList<>();
        for (JsonNode item : data) {
            String url = ImageClientSupport.imageValue(item.path("url"));
            if (url != null) {
                images.add(new GeneratedImage(url, null));
            }
        }
        if (images.isEmpty()) {
            throw new ImageException("[kset-image] SiliconFlow 图片生成响应无可用图片");
        }
        return new ImageGenerationResult(images);
    }
}
