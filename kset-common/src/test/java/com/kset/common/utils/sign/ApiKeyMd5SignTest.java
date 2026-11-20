package com.kset.common.utils.sign;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyMd5SignTest {

    @Test
    void signsSortedParamsWithUppercaseMd5() throws Exception {
        Map<String, String> params = Map.of("b", "2", "a", "1");
        String sign = ApiKeyMd5Sign.sign(params, "secret");

        String plain = "a=1&b=2&key=secret";
        byte[] digest = MessageDigest.getInstance("MD5").digest(plain.getBytes(StandardCharsets.UTF_8));
        StringBuilder expected = new StringBuilder();
        for (byte value : digest) {
            expected.append(String.format("%02X", value));
        }
        assertThat(sign).isEqualTo(expected.toString());
        assertThat(ApiKeyMd5Sign.verifyWithoutTimestamp(params, sign, "secret")).isTrue();
        assertThat(ApiKeyMd5Sign.verifyWithoutTimestamp(params, sign.toLowerCase(), "secret")).isFalse();
    }

    @Test
    void rejectsBlankInput() {
        assertThat(ApiKeyMd5Sign.sign(Map.of(), "secret")).isNull();
        assertThat(ApiKeyMd5Sign.verifyWithoutTimestamp(null, "abc", "secret")).isFalse();
    }
}
