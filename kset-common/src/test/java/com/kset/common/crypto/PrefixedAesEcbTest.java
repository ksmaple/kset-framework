package com.kset.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrefixedAesEcbTest {

    private static final String KEY = "1234567890abcdef";

    @Test
    void roundTripsPrefixedCiphertext() {
        String encrypted = PrefixedAesEcb.encryptIfNeeded("app-secret", KEY);

        assertThat(encrypted).startsWith(PrefixedAesEcb.PREFIX);
        assertThat(PrefixedAesEcb.decryptIfNeeded(encrypted, KEY)).isEqualTo("app-secret");
        assertThat(PrefixedAesEcb.encryptIfNeeded(encrypted, KEY)).isEqualTo(encrypted);
        assertThat(PrefixedAesEcb.decryptIfNeeded("plain", KEY)).isEqualTo("plain");
    }

    @Test
    void keepsPlaintextWhenKeyMissing() {
        assertThat(PrefixedAesEcb.encryptIfNeeded("plain", "")).isEqualTo("plain");
    }

    @Test
    void rejectsShortKey() {
        assertThatThrownBy(() -> PrefixedAesEcb.encryptIfNeeded("plain", "short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16/24/32");
    }
}
