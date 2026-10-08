package com.kset.agent.core.action;

import java.util.List;

/** One task in a model-created execution plan. */
public record PlanTask(String id, String title, List<String> dependencies) {

    public PlanTask {
        if (id == null || id.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("plan task id and title must not be blank");
        }
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
