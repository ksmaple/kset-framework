package com.kset.image.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageGenerationRequestTest {

    @Test
    void rejectsBlankPrompt() {
        assertThatThrownBy(() -> ImageGenerationRequest.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveCount() {
        assertThatThrownBy(() -> ImageGenerationRequest.of("p").withCount(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofReferenceKeepsReferenceImageUrl() {
        ImageGenerationRequest request = ImageGenerationRequest.ofReference("https://cdn/ref.png", "换成夜景")
                .withSize("1024*1024").withCount(2);
        assertThat(request.referenceImageUrl()).isEqualTo("https://cdn/ref.png");
        assertThat(request.size()).isEqualTo("1024*1024");
        assertThat(request.count()).isEqualTo(2);
    }
}
