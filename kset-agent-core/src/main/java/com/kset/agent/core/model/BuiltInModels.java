package com.kset.agent.core.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Immutable catalog of exact model IDs and official token limits, checked 2026-11-04. */
public final class BuiltInModels {

    private static final List<ModelCapacity> MODELS = List.of(
            new ModelCapacity("OpenAI", "gpt-6-astra", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-6.1-sol", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-6.1-terra", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-6-luna", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-6-sol", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-5.6", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-5.6-sol", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-5.6-terra", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-5.6-luna", 1_050_000, 128_000),
            new ModelCapacity("OpenAI", "gpt-4.1", 1_047_576, 32_768),
            new ModelCapacity("Claude", "claude-fable-5-1", 1_000_000, 128_000),
            new ModelCapacity("Claude", "claude-opus-5-5", 1_000_000, 128_000),
            new ModelCapacity("Claude", "claude-sonnet-5-5", 1_000_000, 128_000),
            new ModelCapacity("Claude", "claude-haiku-5-5", 1_000_000, 128_000),
            new ModelCapacity("Claude", "claude-opus-4-5", 200_000, 64_000),
            new ModelCapacity("Claude", "claude-opus-4-5-20251101", 200_000, 64_000),
            new ModelCapacity("Claude", "claude-sonnet-4-5", 200_000, 64_000),
            new ModelCapacity("Claude", "claude-sonnet-4-5-20250929", 200_000, 64_000),
            new ModelCapacity("Claude", "claude-haiku-4-5", 200_000, 64_000),
            new ModelCapacity("Claude", "claude-haiku-4-5-20251001", 200_000, 64_000),
            new ModelCapacity("Gemini", "gemini-3.8-flash", 1_048_576, 65_536),
            new ModelCapacity("Gemini", "gemini-3.6-flash", 1_048_576, 65_536),
            new ModelCapacity("Gemini", "gemini-3.5-flash-lite", 1_048_576, 65_536),
            new ModelCapacity("Gemini", "gemini-2.5-pro", 1_048_576, 65_536),
            new ModelCapacity("Gemini", "gemini-2.5-flash", 1_048_576, 65_536));
    private static final Map<String, ModelCapacity> BY_NAME = MODELS.stream()
            .collect(Collectors.toUnmodifiableMap(
                    model -> model.modelName().toLowerCase(Locale.ROOT), Function.identity()));

    private BuiltInModels() { }

    public static List<ModelCapacity> list() {
        return MODELS;
    }

    public static Optional<ModelCapacity> find(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(modelName.trim().toLowerCase(Locale.ROOT)));
    }
}
