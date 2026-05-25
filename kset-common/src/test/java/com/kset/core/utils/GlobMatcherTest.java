package com.kset.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobMatcherTest {

    @Test
    void matchesSinglePattern() {
        assertTrue(GlobMatcher.matches("/api/users/1", "/api/users/*"));
        assertFalse(GlobMatcher.matches("/api/orders/1", "/api/users/*"));
    }

    @Test
    void matchesMultiplePatterns() {
        assertTrue(GlobMatcher.matches("/health", "/api/**,/health"));
    }
}
