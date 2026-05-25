package com.kset.core.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskingUtilTest {

    @Test
    void masksPasswordInJson() {
        String masked = LogMaskingUtil.maskJson("{\"password\":\"secret123\",\"name\":\"bob\"}");
        assertFalse(masked.contains("secret123"));
        assertTrue(masked.contains("bob"));
    }

    @Test
    void masksPhoneInJson() {
        String masked = LogMaskingUtil.maskJson("{\"mobile\":\"13812345678\"}");
        assertFalse(masked.contains("13812345678"));
        assertTrue(masked.contains("****"));
    }
}
