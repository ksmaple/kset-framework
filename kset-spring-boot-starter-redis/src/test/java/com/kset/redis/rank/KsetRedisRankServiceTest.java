package com.kset.redis.rank;

import com.kset.redis.rank.internal.KsetRedisZSetRankBoard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KsetRedisRankServiceTest {

    @Mock
    private RedisTemplate<String, Object> template;
    @Mock
    private ZSetOperations<String, Object> zOps;

    private KsetRedisRankService rankService;

    @BeforeEach
    void setUp() {
        when(template.opsForZSet()).thenReturn(zOps);
        rankService = KsetRedisRankService.builder(template)
                .keyPrefix("myapp:rank:")
                .build();
    }

    @Test
    void boardUsesKeyPrefixFromBuilder() {
        KsetRedisRankBoard board = rankService.board(
                KsetRedisRankOptions.builder("season-1")
                        .ttl(Duration.ofDays(1))
                        .build());
        assertTrue(board instanceof KsetRedisZSetRankBoard);
        assertEquals("season-1", board.boardId());
    }

    @Test
    void boardUsesExplicitRedisKey() {
        KsetRedisRankOptions options = KsetRedisRankOptions.builder("logical-id")
                .redisKey("custom:rank:key")
                .build();
        assertEquals("custom:rank:key", options.resolveRedisKey("ignored:"));
    }

    @Test
    void resolveRedisKeyFromPrefixAndBoardId() {
        KsetRedisRankOptions options = KsetRedisRankOptions.builder("arena").build();
        assertEquals("myapp:rank:arena", options.resolveRedisKey(rankService.keyPrefix()));
    }

    @Test
    void resolveRedisKeyAllowsColonInBoardId() {
        KsetRedisRankOptions options = KsetRedisRankOptions.builder("arena:2025-w20").build();
        assertEquals("myapp:rank:arena:2025-w20", options.resolveRedisKey(rankService.keyPrefix()));
    }
}
