package com.kset.image.vision;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisionRequestTest {

    @Test
    void requiresExactlyOneImageSource() {
        assertThatThrownBy(() -> new VisionRequest(null, null, null, "p", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VisionRequest("http://a/b.png", "c29tZQ==", null, "p", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(VisionRequest.ofUrl("http://a/b.png", "p").imageUrl()).isEqualTo("http://a/b.png");
        assertThat(VisionRequest.ofBase64("c29tZQ==", "p").imageBase64()).isEqualTo("c29tZQ==");
        assertThat(VisionRequest.ofPath(Path.of("a.png"), "p").imagePath()).isEqualTo(Path.of("a.png"));
    }

    @Test
    void rejectsBlankPrompt() {
        assertThatThrownBy(() -> VisionRequest.ofUrl("http://a/b.png", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMaxTokens() {
        assertThatThrownBy(() -> VisionRequest.ofUrl("http://a/b.png", "p").withMaxTokens(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withResponseSchemaKeepsOtherFields() {
        VisionRequest request = VisionRequest.ofUrl("http://a/b.png", "p")
                .withMaxTokens(1024)
                .withResponseSchema("{\"name\":\"string\"}");
        assertThat(request.responseSchema()).isEqualTo("{\"name\":\"string\"}");
        assertThat(request.maxTokens()).isEqualTo(1024);
        assertThat(request.imageUrl()).isEqualTo("http://a/b.png");
    }
}
