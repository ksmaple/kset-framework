package com.kset.agent.core.action;

import java.util.List;

/** One model-created plan task identified by taskId and linked by dependsOn task IDs. */
public record PlanTask(String taskId, String title, List<String> dependsOn) {

    public PlanTask {
        if (taskId == null || taskId.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("taskId and title must not be blank");
        }
        taskId = taskId.trim();
        title = title.trim();
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
