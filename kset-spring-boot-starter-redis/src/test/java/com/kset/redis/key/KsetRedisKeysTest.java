package com.kset.redis.key;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KsetRedisKeysTest {

    @Test
    void joinSegmentsWithColon() {
        assertEquals("myapp:cache:user:1001",
                KsetRedisKeys.cache("myapp", "user", "profile", "1001"));
    }

    @Test
    void builderFluent() {
        assertEquals("sys:order:item:42",
                KsetRedisKeys.builder("sys").segment("order").segment("item").id("42").build());
    }

    @Test
    void joinPrefixNormalizesColon() {
        assertEquals("myapp:cache:user:1",
                KsetRedisKeys.joinPrefixSegments("myapp:", "cache", "user", "1"));
        assertEquals("myapp:cache:user:1",
                KsetRedisKeys.joinPrefixSegments("myapp", "cache", "user", "1"));
    }

    @Test
    void joinPrefixSkipsDuplicate() {
        assertEquals("myapp:cache:user:1",
                KsetRedisKeys.joinPrefix("myapp:", "myapp:cache:user:1"));
    }

    @Test
    void appendSuffix() {
        assertEquals("myapp:rank:arena:groups",
                KsetRedisKeys.append("myapp:rank:arena", "groups"));
    }

    @Test
    void rankBoardKey() {
        assertEquals("game:rank:season-1", KsetRedisKeys.rank("game", "season-1"));
    }

    @Test
    void rejectColonInSegment() {
        assertThrows(IllegalArgumentException.class,
                () -> KsetRedisKeys.join("a", "b:c", "d"));
    }

    @Test
    void normalizeKeyCollapsesEmptyParts() {
        assertEquals("a:b:c", KsetRedisKeys.normalizeKey("a::b:c:"));
    }
}
