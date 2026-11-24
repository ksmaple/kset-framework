package com.kset.image.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.kset.image.client.OpenAiCompatibleImageGenerator;
import com.kset.image.client.OpenAiCompatibleVisionClient;
import com.kset.image.client.SiliconFlowImageGenerator;
import com.kset.image.config.ImageProperties;
import com.kset.image.generation.ImageGenerator;
import com.kset.image.vision.VisionClient;

/**
 * kset 图片 AI 能力装配：配置 kset.image.api-key 后自动启用图片理解与图片生成客户端；
 * 图片生成支持显式选择 OpenAI 或 SiliconFlow 协议。
 * 未配置 api-key 时不装配客户端并在启动时告警。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.image", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ImageProperties.class)
public class ImageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ImageAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    @Conditional(NonBlankApiKeyCondition.class)
    @ConditionalOnProperty(prefix = "kset.image.vision", name = "enabled", havingValue = "true", matchIfMissing = true)
    public VisionClient visionClient(ImageProperties properties) {
        VisionClient client = new OpenAiCompatibleVisionClient(properties.getBaseUrl(), properties.getApiKey(), properties.getVision());
        log.info("[kset-image] 图片理解已启用，model={}", properties.getVision().getModel());
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(NonBlankApiKeyCondition.class)
    @ConditionalOnProperty(prefix = "kset.image.generation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ImageGenerator imageGenerator(ImageProperties properties) {
        ImageProperties.Generation config = properties.getGeneration();
        Assert.notNull(config.getProvider(), "generation.provider 不能为 null");
        ImageGenerator client = switch (config.getProvider()) {
            case OPENAI -> new OpenAiCompatibleImageGenerator(properties.getBaseUrl(), properties.getApiKey(), config);
            case SILICONFLOW -> new SiliconFlowImageGenerator(properties.getBaseUrl(), properties.getApiKey(), config);
        };
        log.info("[kset-image] 图片生成已启用，model={}", properties.getGeneration().getModel());
        return client;
    }

    /**
     * 理解与生成均未装配且原因是缺少 api-key 时，启动时明确告警；两项能力被显式关闭时不误报。
     */
    @Bean
    @ConditionalOnMissingBean({VisionClient.class, ImageGenerator.class})
    public ApplicationRunner ksetImageMissingKeyWarner(ImageProperties properties) {
        return args -> {
            if ((properties.getVision().isEnabled() || properties.getGeneration().isEnabled())
                    && !StringUtils.hasText(properties.getApiKey())) {
                log.warn("[kset-image] 未配置 kset.image.api-key，图片理解与生成能力不生效");
            }
        };
    }

    static class NonBlankApiKeyCondition extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            boolean configured = StringUtils.hasText(context.getEnvironment().getProperty("kset.image.api-key"));
            return configured ? ConditionOutcome.match("图片 API Key 已配置")
                    : ConditionOutcome.noMatch("图片 API Key 未配置或为空白");
        }
    }
}
