package com.kset.image.client;

import org.junit.jupiter.api.Test;

import com.kset.image.ImageException;
import com.kset.image.generation.ImageGenerationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleImageGeneratorTest {

    @Test
    void parseResultReadsUrls() {
        String body = "{\"data\":[{\"url\":\"https://cdn/a.png\"},{\"url\":\"https://cdn/b.png\"}]}";
        ImageGenerationResult result = OpenAiCompatibleImageGenerator.parseResult(body);
        assertThat(result.images()).hasSize(2);
        assertThat(result.images().get(0).url()).isEqualTo("https://cdn/a.png");
    }

    @Test
    void parseResultReadsBase64() {
        String body = "{\"data\":[{\"b64_json\":\"aGVsbG8=\"}]}";
        ImageGenerationResult result = OpenAiCompatibleImageGenerator.parseResult(body);
        assertThat(result.images().get(0).base64()).isEqualTo("aGVsbG8=");
    }

    @Test
    void parseResultRejectsEmptyData() {
        assertThatThrownBy(() -> OpenAiCompatibleImageGenerator.parseResult("{\"data\":[]}"))
                .isInstanceOf(ImageException.class);
    }

    @Test
    void parseResultRejectsItemsWithoutImage() {
        assertThatThrownBy(() -> OpenAiCompatibleImageGenerator.parseResult("{\"data\":[{\"foo\":1}]}"))
                .isInstanceOf(ImageException.class);
    }
}
