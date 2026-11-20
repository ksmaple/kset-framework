package com.kset.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base64CodecTest {

    @Test
    void preservesStandardBase64Format() {
        byte[] value = "Man".getBytes(StandardCharsets.US_ASCII);

        assertThat(Base64Codec.encode(value)).isEqualTo("TWFu");
        assertThat(Base64Codec.decode("TWFu")).isEqualTo(value);
        assertThat(Base64Codec.decode("TQ==")).isEqualTo("M".getBytes(StandardCharsets.US_ASCII));
        assertThat(Base64Codec.decode("TQ")).isEqualTo("M".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void rejectsMalformedInput() {
        for (String value : new String[] {"T=Fu", "TWFu\n", "T", "TQ=", "TQ==="}) {
            assertThatThrownBy(() -> Base64Codec.decode(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
