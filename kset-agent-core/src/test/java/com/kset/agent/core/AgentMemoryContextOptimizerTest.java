package com.kset.agent.core;

import com.kset.agent.core.memory.AgentMemoryContext;
import com.kset.agent.core.memory.MemoryPriority;
import com.kset.agent.core.memory.MemoryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMemoryContextOptimizerTest {

    private final AgentMemoryContextOptimizer optimizer = new AgentMemoryContextOptimizer();

    private static AgentMemoryContext.Entry entry(String content, MemoryPriority priority) {
        return new AgentMemoryContext.Entry("user", MemoryType.FACT, content, null, false, priority, null);
    }

    private static AgentMemoryContext.Entry entry(String content, MemoryPriority priority, String summary) {
        return new AgentMemoryContext.Entry("user", MemoryType.FACT, content, null, false, priority, summary);
    }

    @Test
    void nullContextReturnsEmptyResult() {
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(null, "task");
        assertEquals(0, result.originalCount());
        assertEquals(0, result.selectedCount());
        assertFalse(result.compressed());
        assertTrue(result.context().isEmpty());
    }

    @Test
    void emptyContextReturnsEmptyResult() {
        AgentMemoryContext context = new AgentMemoryContext(List.of(), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task");
        assertEquals(0, result.originalCount());
        assertEquals(0, result.selectedCount());
        assertFalse(result.compressed());
    }

    @Test
    void fullBudgetDeduplicatesNormalizedContent() {
        AgentMemoryContext context = new AgentMemoryContext(List.of(
                entry("用户偏好 中文 回复", MemoryPriority.MEDIUM),
                entry("用户偏好中文回复", MemoryPriority.MEDIUM)), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task");
        assertEquals(2, result.originalCount());
        assertEquals(1, result.selectedCount());
        assertEquals(1, result.omittedCount());
        assertTrue(result.compressed());
    }

    @Test
    void entriesAreRankedByPriorityFirst() {
        AgentMemoryContext context = new AgentMemoryContext(List.of(
                entry("低优先级内容", MemoryPriority.LOW),
                entry("高优先级内容", MemoryPriority.HIGH),
                entry("中优先级内容", MemoryPriority.MEDIUM)), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task");
        assertEquals(3, result.selectedCount());
        List<AgentMemoryContext.Entry> selected = result.context().currentSession();
        assertEquals(MemoryPriority.HIGH, selected.get(0).priority());
        assertEquals(MemoryPriority.MEDIUM, selected.get(1).priority());
        assertEquals(MemoryPriority.LOW, selected.get(2).priority());
        // 全额预算不做内容截断，仅标准化去重，结果均标记为未压缩内容
        assertEquals(0, result.compressedEntryCount());
    }

    @Test
    void discardableEntriesAreDroppedFirstWhenCompressing() {
        AgentMemoryContext context = new AgentMemoryContext(List.of(
                entry("重要内容".repeat(10), MemoryPriority.HIGH),
                entry("可丢弃内容".repeat(10), MemoryPriority.DISCARDABLE)), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task", 0.5d);
        assertEquals(2, result.originalCount());
        assertEquals(1, result.selectedCount());
        assertEquals(1, result.omittedCount());
        assertEquals(MemoryPriority.HIGH, result.context().currentSession().get(0).priority());
        assertTrue(result.compressed());
    }

    @Test
    void discardableEntriesAreKeptAtFullBudget() {
        AgentMemoryContext context = new AgentMemoryContext(List.of(
                entry("可丢弃内容", MemoryPriority.DISCARDABLE)), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task");
        assertEquals(1, result.selectedCount());
        assertFalse(result.compressed());
    }

    @Test
    void highPriorityEntryIsSummarizedWhenBudgetTooSmall() {
        String content = "x".repeat(100);
        AgentMemoryContext context = new AgentMemoryContext(List.of(
                entry(content, MemoryPriority.HIGH)), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task", 0.1d);
        assertEquals(1, result.selectedCount());
        AgentMemoryContext.Entry selected = result.context().currentSession().get(0);
        assertEquals(10, selected.content().length());
        assertEquals(10, result.selectedChars());
        assertEquals(1, result.compressedEntryCount());
        assertTrue(result.compressed());
    }

    @Test
    void highPriorityEntryFallsBackToSummaryTextWhenAvailable() {
        String content = "x".repeat(100);
        AgentMemoryContext context = new AgentMemoryContext(List.of(
                entry(content, MemoryPriority.HIGH, "一句话摘要内容") ), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task", 0.1d);
        AgentMemoryContext.Entry selected = result.context().currentSession().get(0);
        // 摘要（7 字符）短于剩余预算（10 字符），整段保留
        assertEquals("一句话摘要内容", selected.content());
    }

    @Test
    void relevantEntryWinsWithinSamePriorityWhenBudgetIsTight() {
        String relevant = "java " + "x".repeat(55);
        String irrelevant = "y".repeat(60);
        AgentMemoryContext context = new AgentMemoryContext(List.of(
                entry(relevant, MemoryPriority.MEDIUM),
                entry(irrelevant, MemoryPriority.MEDIUM)), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "java", 0.5d);
        assertEquals(1, result.selectedCount());
        assertEquals(relevant, result.context().currentSession().get(0).content());
        assertEquals(60, result.selectedChars());
    }

    @Test
    void crossSessionEntriesKeepTheirSourceBucket() {
        AgentMemoryContext context = new AgentMemoryContext(
                List.of(entry("当前会话内容", MemoryPriority.HIGH)),
                List.of(entry("跨会话内容", MemoryPriority.MEDIUM)));
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task");
        assertEquals(1, result.context().currentSession().size());
        assertEquals(1, result.context().crossSession().size());
        assertEquals("当前会话内容", result.context().currentSession().get(0).content());
        assertEquals("跨会话内容", result.context().crossSession().get(0).content());
    }

    @Test
    void compressionRatioIsClampedToAtLeastFivePercent() {
        String content = "x".repeat(100);
        AgentMemoryContext context = new AgentMemoryContext(List.of(
                entry(content, MemoryPriority.HIGH)), List.of());
        AgentMemoryContextOptimizer.OptimizedMemory result = optimizer.optimize(context, "task", 0.0d);
        // 比例被钳制到 0.05，目标字符数为 5
        assertEquals(5, result.selectedChars());
    }
}
