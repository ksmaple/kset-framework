package com.kset.agent.core.model;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable model capacities for one Kernel or Runtime, with custom entries taking precedence. */
public final class ModelCapacityCatalog {

    private final List<ModelCapacity> models;
    private final Map<String, ModelCapacity> byName;

    private ModelCapacityCatalog(List<ModelCapacity> overrides) {
        Map<String, ModelCapacity> entries = new LinkedHashMap<>();
        for (ModelCapacity model : BuiltInModels.list()) {
            entries.put(key(model.modelName()), model);
        }
        Set<String> customNames = new HashSet<>();
        for (ModelCapacity model : overrides) {
            Objects.requireNonNull(model, "model capacity");
            String name = key(model.modelName());
            if (!customNames.add(name)) {
                throw new IllegalArgumentException("duplicate model capacity: " + model.modelName());
            }
            entries.put(name, model);
        }
        models = List.copyOf(entries.values());
        byName = Map.copyOf(entries);
    }

    public static ModelCapacityCatalog withOverrides(List<ModelCapacity> overrides) {
        return new ModelCapacityCatalog(Objects.requireNonNull(overrides, "overrides"));
    }

    public List<ModelCapacity> list() {
        return models;
    }

    public Optional<ModelCapacity> find(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(key(modelName)));
    }

    private static String key(String modelName) {
        return modelName.trim().toLowerCase(Locale.ROOT);
    }
}
