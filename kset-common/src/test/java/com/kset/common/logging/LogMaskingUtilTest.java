package com.kset.common.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.kset.common.utils.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogMaskingUtilTest {

    @Test
    void masksPasswordAndKeepsUnrelatedFields() {
        String masked = LogMaskingUtil.maskJson("""
                {"password":"secret","monkey":"banana","author":"alice"}
                """);

        JsonNode node = JsonUtil.readTree(masked);
        assertThat(node.get("password").asText()).isEqualTo("[REDACTED]");
        assertThat(node.get("monkey").asText()).isEqualTo("banana");
        assertThat(node.get("author").asText()).isEqualTo("alice");
    }

    @Test
    void masksCamelCaseApiKey() {
        String masked = LogMaskingUtil.maskJson("{\"apiKey\":\"abc\"}");

        assertThat(JsonUtil.readTree(masked).get("apiKey").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void emailWithoutAtDoesNotThrow() {
        assertThatCode(() -> LogMaskingUtil.maskJson("{\"email\":\"not-an-email\"}"))
                .doesNotThrowAnyException();

        String masked = LogMaskingUtil.maskJson("{\"email\":\"not-an-email\"}");
        assertThat(JsonUtil.readTree(masked).get("email").asText()).isEqualTo("****");
    }

    @Test
    void masksEmailDomain() {
        String masked = LogMaskingUtil.maskJson("{\"email\":\"alice@example.com\"}");

        assertThat(JsonUtil.readTree(masked).get("email").asText()).isEqualTo("a***@example.com");
    }

    @Test
    void masksNumericPhoneAndArrayValues() {
        String masked = LogMaskingUtil.maskJson("""
                {"phone":13800138000,"phones":["13900139000"]}
                """);

        JsonNode node = JsonUtil.readTree(masked);
        assertThat(node.get("phone").asText()).isEqualTo("138****8000");
        assertThat(node.get("phones").get(0).asText()).isEqualTo("139****9000");
    }
}
