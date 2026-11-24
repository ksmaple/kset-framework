package com.kset.image.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

import com.kset.image.ImageException;
import com.kset.image.config.ImageProperties;
import com.kset.image.generation.ImageGenerationRequest;
import com.kset.image.generation.ImageGenerationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiliconFlowImageGeneratorTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<JsonNode> payload = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"images\":[{\"url\":\"https://example.com/generated.png\"}]}";

    @BeforeEach
    void startProviderStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            payload.set(ImageClientSupport.MAPPER.readTree(exchange.getRequestBody()));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopProviderStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void generatesWithNativeRequestAndParsesImageUrl() {
        ImageGenerationResult result = client("Qwen/Qwen-Image")
                .generate(ImageGenerationRequest.of("orange cat").withSize("1328x1328"));

        assertThat(path.get()).isEqualTo("/v1/images/generations");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(payload.get().path("model").asText()).isEqualTo("Qwen/Qwen-Image");
        assertThat(payload.get().path("prompt").asText()).isEqualTo("orange cat");
        assertThat(payload.get().path("image_size").asText()).isEqualTo("1328x1328");
        assertThat(payload.get().has("size")).isFalse();
        assertThat(payload.get().has("n")).isFalse();
        assertThat(result.images()).hasSize(1);
        assertThat(result.images().getFirst().url()).isEqualTo("https://example.com/generated.png");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void editsUsingImageFieldAndSameGenerationEndpoint() {
        client("Qwen/Qwen-Image-Edit-2509")
                .generate(ImageGenerationRequest.ofReference("https://example.com/input.png", "blue sky"));

        assertThat(path.get()).isEqualTo("/v1/images/generations");
        assertThat(payload.get().path("image").asText()).isEqualTo("https://example.com/input.png");
        assertThat(payload.get().has("images")).isFalse();
        assertThat(payload.get().has("image_size")).isFalse();
    }

    @Test
    void rejectsUnsupportedCountAndReferenceSizeBeforeSending() {
        SiliconFlowImageGenerator generator = client("Qwen/Qwen-Image");
        assertThatThrownBy(() -> generator.generate(ImageGenerationRequest.of("cat").withCount(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(
                ImageGenerationRequest.ofReference("https://example.com/input.png", "cat").withSize("1024x1024")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.get()).isZero();
    }

    @Test
    void propagatesProviderFailureWithoutRetrying() {
        responseStatus = 503;
        responseBody = "{\"code\":50505,\"message\":\"Model service overloaded\"}";
        assertThatThrownBy(() -> client("Qwen/Qwen-Image").generate(ImageGenerationRequest.of("cat")))
                .isInstanceOf(ImageException.class)
                .hasCauseInstanceOf(HttpServerErrorException.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void rejectsMissingNativeImageData() {
        assertThatThrownBy(() -> SiliconFlowImageGenerator.parseResult("{\"data\":[{\"url\":\"https://example.com/a.png\"}]}"))
                .isInstanceOf(ImageException.class);
        assertThatThrownBy(() -> SiliconFlowImageGenerator.parseResult("{\"images\":[{\"url\":\" \"}]}"))
                .isInstanceOf(ImageException.class);
    }

    private SiliconFlowImageGenerator client(String model) {
        ImageProperties.Generation config = new ImageProperties.Generation();
        config.setModel(model);
        return new SiliconFlowImageGenerator("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key", config);
    }
}
