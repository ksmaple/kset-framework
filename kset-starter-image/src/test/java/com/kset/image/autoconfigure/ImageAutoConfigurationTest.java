package com.kset.image.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.kset.image.client.OpenAiCompatibleImageGenerator;
import com.kset.image.client.SiliconFlowImageGenerator;
import com.kset.image.config.ImageProperties;
import com.kset.image.generation.ImageGenerator;
import com.kset.image.vision.VisionClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ImageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ImageAutoConfiguration.class))
            .withPropertyValues("kset.image.api-key=test-key");

    @Test
    void retainsOpenAiDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ImageGenerator.class).hasSingleBean(VisionClient.class);
            assertThat(context.getBean(ImageGenerator.class)).isInstanceOf(OpenAiCompatibleImageGenerator.class);
        });
    }

    @Test
    void bindsSiliconFlowConfigurationAndSelectsNativeGenerator() {
        runner.withPropertyValues("kset.image.generation.provider=siliconflow",
                "kset.image.base-url=https://api.siliconflow.cn/v1",
                "kset.image.vision.model=Qwen/Qwen3-VL-32B-Instruct",
                "kset.image.generation.model=Qwen/Qwen-Image",
                "kset.image.generation.default-size=1328x1328")
                .run(context -> {
                    assertThat(context).hasSingleBean(ImageGenerator.class).hasSingleBean(VisionClient.class);
                    assertThat(context.getBean(ImageGenerator.class)).isInstanceOf(SiliconFlowImageGenerator.class);
                    ImageProperties properties = context.getBean(ImageProperties.class);
                    assertThat(properties.getGeneration().getProvider()).isEqualTo(ImageProperties.Generation.Provider.SILICONFLOW);
                    assertThat(properties.getGeneration().getDefaultSize()).isEqualTo("1328x1328");
                    assertThat(properties.getVision().getModel()).isEqualTo("Qwen/Qwen3-VL-32B-Instruct");
                });
    }

    @Test
    void doesNotCreateClientsWithoutKey() {
        runner.withPropertyValues("kset.image.api-key=")
                .run(context -> assertThat(context).doesNotHaveBean(ImageGenerator.class).doesNotHaveBean(VisionClient.class));
    }

    @Test
    void respectsHostGeneratorOverride() {
        ImageGenerator custom = mock(ImageGenerator.class);
        runner.withPropertyValues("kset.image.generation.provider=siliconflow")
                .withBean(ImageGenerator.class, () -> custom)
                .run(context -> assertThat(context.getBean(ImageGenerator.class)).isSameAs(custom));
    }
}
