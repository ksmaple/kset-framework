package com.kset.agent.context;

/**
 * Prompt 候选版本覆盖上下文，仅用于隔离评估，不改变线上激活版本。
 */
public final class PromptVersionOverrideContext {

    private static final ThreadLocal<OverrideVersion> HOLDER = new ThreadLocal<>();

    private PromptVersionOverrideContext() {
    }

    public static OverrideVersion current() {
        return HOLDER.get();
    }

    public static Scope withVersion(String promptCode, Long versionId) {
        HOLDER.set(new OverrideVersion(promptCode, versionId));
        return HOLDER::remove;
    }

    public static void markUsed() {
        OverrideVersion current = HOLDER.get();
        if (current != null) {
            current.used = true;
        }
    }

    public static final class OverrideVersion {
        private final String promptCode;
        private final Long versionId;
        private boolean used;

        private OverrideVersion(String promptCode, Long versionId) {
            this.promptCode = promptCode;
            this.versionId = versionId;
        }

        public String promptCode() {
            return promptCode;
        }

        public Long versionId() {
            return versionId;
        }

        public boolean used() {
            return used;
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
