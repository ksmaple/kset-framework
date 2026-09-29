package com.kset.agent.core.memory;

import java.util.List;

/**
 * Agent 记忆上下文，按当前会话与跨会话来源隔离。
 */
public record AgentMemoryContext(List<Entry> currentSession, List<Entry> crossSession) {

    public AgentMemoryContext {
        currentSession = currentSession == null ? List.of() : List.copyOf(currentSession);
        crossSession = crossSession == null ? List.of() : List.copyOf(crossSession);
    }

    public boolean isEmpty() {
        return currentSession.isEmpty() && crossSession.isEmpty();
    }

    public record Entry(String role, MemoryType memoryType, String content,
                        String audienceProfile, boolean instructionAllowed,
                        MemoryPriority priority, String summary) {

        public Entry {
            priority = priority == null ? MemoryPriority.MEDIUM : priority;
        }

        public Entry(String role, MemoryType memoryType, String content,
                     String audienceProfile, boolean instructionAllowed) {
            this(role, memoryType, content, audienceProfile, instructionAllowed,
                    MemoryPriority.MEDIUM, null);
        }
    }
}
