package com.kset.redis.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KsetRedisTtlPolicyTest {

    @Test
    void requireTtlUsesDefaultWhenNull() {
        KsetRedisTtlPolicy policy = new KsetRedisTtlPolicy(Duration.ofMinutes(10), null);
        assertEquals(Duration.ofMinutes(10), policy.requireTtl(null));
    }

    @Test
    void requireTtlRejectsPermanent() {
        KsetRedisTtlPolicy policy = new KsetRedisTtlPolicy(Duration.ofMinutes(10), null);
        assertThrows(IllegalArgumentException.class, () -> policy.requireTtl(Duration.ZERO));
    }

    @Test
    void requireTtlCapsAtMax() {
        KsetRedisTtlPolicy policy = new KsetRedisTtlPolicy(Duration.ofMinutes(10), Duration.ofHours(1));
        assertEquals(Duration.ofHours(1), policy.requireTtl(Duration.ofDays(1)));
    }
}
