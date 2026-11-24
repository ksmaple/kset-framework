package com.kset.image.client;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kset.image.ImageException;
import com.kset.image.vision.VisionRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleVisionClientTest {

    @Test
    void extractContentReadsFirstChoice() {
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"一只猫\"}}]}";
        assertThat(OpenAiCompatibleVisionClient.extractContent(body)).isEqualTo("一只猫");
    }

    @Test
    void extractContentRejectsMissingContent() {
        assertThatThrownBy(() -> OpenAiCompatibleVisionClient.extractContent("{\"choices\":[]}"))
                .isInstanceOf(ImageException.class);
    }

    @Test
    void parseStructuredParamsStripsCodeFence() {
        VisionRequest request = VisionRequest.ofUrl("http://a/b.png", "p")
                .withResponseSchema("{\"name\":\"string\"}");
        Map<String, Object> params = OpenAiCompatibleVisionClient
                .parseStructuredParams(request, "```json\n{\"name\":\"铭牌\",\"voltage\":\"220V\"}\n```");
        assertThat(params.get("name")).isEqualTo("铭牌");
        assertThat(params.get("voltage")).isEqualTo("220V");
    }

    @Test
    void parseStructuredParamsReturnsEmptyWithoutSchema() {
        VisionRequest request = VisionRequest.ofUrl("http://a/b.png", "p");
        assertThat(OpenAiCompatibleVisionClient.parseStructuredParams(request, "{\"a\":1}")).isEmpty();
    }

    @Test
    void parseStructuredParamsFallsBackToEmptyOnInvalidJson() {
        VisionRequest request = VisionRequest.ofUrl("http://a/b.png", "p")
                .withResponseSchema("{\"name\":\"string\"}");
        assertThat(OpenAiCompatibleVisionClient.parseStructuredParams(request, "不是JSON")).isEmpty();
    }
}
