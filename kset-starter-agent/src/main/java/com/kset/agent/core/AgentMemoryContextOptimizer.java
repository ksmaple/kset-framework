package com.kset.agent.core;

import com.kset.agent.memory.AgentMemoryContext;
import com.kset.agent.memory.MemoryPriority;
import com.kset.agent.memory.MemoryType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 按当前任务和输入预算筛选 Agent 可用的会话记忆。 */
@Component
public class AgentMemoryContextOptimizer {

    private static final int MAX_TASK_TERMS = 64;
    private static final Pattern TERM_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[\\p{L}\\p{N}_.$/-]{2,}");

    public OptimizedMemory optimize(AgentMemoryContext memoryContext, String currentTask) {
        return optimize(memoryContext, currentTask, 1.0d);
    }

    /**
     * 首次请求只做标准化去重；模型服务明确报告上下文或 Token 限额后才按比例淘汰。
     */
    public OptimizedMemory optimize(AgentMemoryContext memoryContext, String currentTask, double compressionRatio) {
        if (memoryContext == null || memoryContext.isEmpty()) {
            return OptimizedMemory.empty();
        }
        double ratio = Math.max(0.05d, Math.min(1.0d, compressionRatio));
        Set<String> taskTerms = extractTerms(currentTask);
        Map<String, Candidate> unique = new LinkedHashMap<>();
        addCandidates(unique, memoryContext.currentSession(), MemorySource.CURRENT_SESSION, taskTerms);
        addCandidates(unique, memoryContext.crossSession(), MemorySource.CROSS_SESSION, taskTerms);

        List<Candidate> ranked = unique.values().stream()
                .sorted(Comparator.comparingInt((Candidate candidate) -> candidate.entry().priority().weight()).reversed()
                        .thenComparing(Comparator.comparingInt(Candidate::score).reversed())
                        .thenComparing(Comparator.comparingInt(Candidate::recency).reversed()))
                .toList();
        int originalChars = ranked.stream().mapToInt(candidate -> candidate.entry().content().length()).sum();
        int targetChars = ratio >= 1.0d ? originalChars : Math.max(1, (int) Math.floor(originalChars * ratio));
        List<AgentMemoryContext.Entry> currentSession = new ArrayList<>();
        List<AgentMemoryContext.Entry> crossSession = new ArrayList<>();
        int selectedChars = 0;
        int compressedEntries = 0;
        for (Candidate candidate : ranked) {
            AgentMemoryContext.Entry entry = candidate.entry();
            if (ratio < 1.0d && entry.priority() == MemoryPriority.DISCARDABLE) {
                continue;
            }
            int remaining = targetChars - selectedChars;
            if (remaining <= 0) {
                break;
            }
            String content = entry.content();
            if (content.length() > remaining) {
                content = entry.priority() == MemoryPriority.HIGH
                        ? summaryWithin(entry, remaining) : "";
            }
            if (content.isBlank()) {
                continue;
            }
            boolean compressed = content.length() < entry.content().length();
            AgentMemoryContext.Entry selected = new AgentMemoryContext.Entry(
                    entry.role(), entry.memoryType(), content, entry.audienceProfile(), false,
                    entry.priority(), entry.summary());
            if (candidate.source() == MemorySource.CURRENT_SESSION) {
                currentSession.add(selected);
            } else {
                crossSession.add(selected);
            }
            selectedChars += content.length();
            if (compressed) {
                compressedEntries++;
            }
        }
        int originalCount = memoryContext.currentSession().size() + memoryContext.crossSession().size();
        int selectedCount = currentSession.size() + crossSession.size();
        return new OptimizedMemory(new AgentMemoryContext(currentSession, crossSession),
                originalCount, selectedCount, Math.max(0, originalCount - selectedCount),
                selectedChars, compressedEntries, selectedCount < originalCount || compressedEntries > 0);
    }

    private String summaryWithin(AgentMemoryContext.Entry entry, int remaining) {
        String summary = entry.summary();
        if (summary == null || summary.isBlank()) {
            summary = entry.content();
        }
        return limit(summary, remaining);
    }

    private void addCandidates(Map<String, Candidate> unique,
                               List<AgentMemoryContext.Entry> entries,
                               MemorySource source,
                               Set<String> taskTerms) {
        for (int index = 0; index < entries.size(); index++) {
            AgentMemoryContext.Entry entry = entries.get(index);
            if (entry == null || entry.content() == null || entry.content().isBlank()) {
                continue;
            }
            String normalized = normalize(entry.content());
            int recency = source == MemorySource.CURRENT_SESSION ? index + 1 : entries.size() - index;
            Candidate candidate = new Candidate(entry, source, recency,
                    score(entry, source, index, entries.size(), normalized, taskTerms));
            unique.merge(normalized, candidate,
                    (existing, replacement) -> replacement.score() > existing.score() ? replacement : existing);
        }
    }

    private int score(AgentMemoryContext.Entry entry,
                      MemorySource source,
                      int index,
                      int total,
                      String normalizedContent,
                      Set<String> taskTerms) {
        int score = source == MemorySource.CURRENT_SESSION ? 400 : 0;
        int recency = source == MemorySource.CURRENT_SESSION ? index + 1 : total - index;
        score += Math.max(0, recency) * 5;
        score += memoryTypePriority(entry.memoryType());
        for (String term : taskTerms) {
            if (normalizedContent.contains(term)) {
                score += Math.min(80, 20 + term.length() * 5);
            }
        }
        return score;
    }

    private int memoryTypePriority(MemoryType type) {
        if (type == null) {
            return 80;
        }
        return switch (type) {
            case PREFERENCE -> 300;
            case FACT -> 260;
            case SUMMARY -> 220;
            case TOOL_RESULT -> 160;
            case USER_QUERY -> 120;
            case AI_RESPONSE -> 80;
        };
    }

    private Set<String> extractTerms(String task) {
        if (task == null || task.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = TERM_PATTERN.matcher(normalize(task));
        while (matcher.find()) {
            String term = matcher.group();
            terms.add(term);
            if (containsHan(term) && term.length() > 3) {
                for (int i = 0; i < term.length() - 1 && terms.size() < MAX_TASK_TERMS; i++) {
                    terms.add(term.substring(i, i + 2));
                }
            }
            if (terms.size() >= MAX_TASK_TERMS) {
                break;
            }
        }
        return terms;
    }

    private boolean containsHan(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint)
                == Character.UnicodeScript.HAN);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private String limit(String value, int maxChars) {
        if (value == null || maxChars <= 0) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private enum MemorySource {
        CURRENT_SESSION,
        CROSS_SESSION
    }

    private record Candidate(AgentMemoryContext.Entry entry, MemorySource source, int recency, int score) {
    }

    public record OptimizedMemory(AgentMemoryContext context,
                                  int originalCount,
                                  int selectedCount,
                                  int omittedCount,
                                  int selectedChars,
                                  int compressedEntryCount,
                                  boolean compressed) {

        private static OptimizedMemory empty() {
            return new OptimizedMemory(new AgentMemoryContext(null, null), 0, 0, 0, 0, 0, false);
        }
    }
}
