package com.kset.redis.rank;

import com.kset.redis.rank.internal.KsetRedisZSetRankBoard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KsetRedisZSetRankBoardTest {

    @Mock
    private RedisTemplate<String, Object> template;
    @Mock
    private ZSetOperations<String, Object> zOps;

    private KsetRedisRankBoard board;

    @BeforeEach
    void setUp() {
        when(template.opsForZSet()).thenReturn(zOps);
        board = new KsetRedisZSetRankBoard("season-1", "kset:rank:season-1", template,
                KsetRedisRankOrder.HIGH_SCORE_FIRST, null);
    }

    @Test
    void topReturnsHighestScoresFirst() {
        Set<ZSetOperations.TypedTuple<Object>> tuples = new LinkedHashSet<>();
        tuples.add(ZSetOperations.TypedTuple.of("u1", 100D));
        tuples.add(ZSetOperations.TypedTuple.of("u2", 80D));
        when(zOps.reverseRangeWithScores(eq("kset:rank:season-1"), eq(0L), eq(1L))).thenReturn(tuples);

        List<KsetRedisRankEntry> top = board.top(2);

        assertEquals(2, top.size());
        assertEquals(1, top.get(0).rank());
        assertEquals("u1", top.get(0).member());
        assertEquals(100D, top.get(0).score());
        assertEquals(2, top.get(1).rank());
    }

    @Test
    void rankOfUsesReverseRank() {
        when(zOps.reverseRank("kset:rank:season-1", "u1")).thenReturn(0L);
        assertEquals(Optional.of(1), board.rankOf("u1"));
    }

    @Test
    void incrementNotifiesOnRankChange() {
        when(zOps.reverseRank("kset:rank:season-1", "u1")).thenReturn(1L, 0L);
        when(zOps.score("kset:rank:season-1", "u1")).thenReturn(10D, 20D);
        when(zOps.incrementScore("kset:rank:season-1", "u1", 10D)).thenReturn(20D);

        AtomicReference<KsetRedisRankChangeEvent> captured = new AtomicReference<>();
        board.increment("u1", 10D, captured::set);

        assertTrue(captured.get().rankChanged());
        assertEquals(2, captured.get().previousRank());
        assertEquals(1, captured.get().newRank());
    }

    @Test
    void incrementScore() {
        when(zOps.reverseRank("kset:rank:season-1", "u1")).thenReturn(null, 0L);
        when(zOps.score("kset:rank:season-1", "u1")).thenReturn(null, 50D);
        when(zOps.add(eq("kset:rank:season-1"), eq("u1"), anyDouble())).thenReturn(true);

        board.setScore("u1", 50D);

        verify(zOps).add("kset:rank:season-1", "u1", 50D);
    }
}
