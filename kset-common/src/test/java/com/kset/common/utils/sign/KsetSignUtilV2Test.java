package com.kset.common.utils.sign;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KsetSignUtilV2Test {

    @Test
    void bindsPathBodyAndParameterBoundaries() {
        KsetSignUtil signer = KsetSignUtil.of("secret");
        String body = KsetSignUtil.sha256("{\"id\":1}".getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = Map.of("X-App-Key", "partner", "X-Timestamp", "123");
        String signature = signer.signV2("POST", "/orders", body,
                Map.of("a", "1b2", "b", "3"), headers);

        assertThat(signer.verifyV2(signature, "POST", "/orders", body,
                Map.of("a", "1b2", "b", "3"), headers)).isTrue();
        assertThat(signer.verifyV2(signature, "POST", "/payments", body,
                Map.of("a", "1b2", "b", "3"), headers)).isFalse();
        assertThat(signer.verifyV2(signature, "POST", "/orders",
                KsetSignUtil.sha256("{\"id\":2}".getBytes(StandardCharsets.UTF_8)),
                Map.of("a", "1b2", "b", "3"), headers)).isFalse();
        assertThat(signer.verifyV2(signature, "POST", "/orders", body,
                Map.of("a", "1", "b", "2b3"), headers)).isFalse();
        assertThat(signer.verifyV2(signature, "POST", "/orders", body,
                Map.of("a", "1b2", "b", "3"), Map.of("X-App-Key", "other", "X-Timestamp", "123")))
                .isFalse();
    }
}
