package com.kset.agent.core.action;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record TaskPlanAction(String summary, List<PlanTask> tasks) implements AgentAction {

    public TaskPlanAction {
        if (summary == null || summary.isBlank() || tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("task plan requires a summary and at least one task");
        }
        tasks = List.copyOf(tasks);
        Set<String> ids = new HashSet<>();
        for (PlanTask task : tasks) {
            if (!ids.add(task.taskId())) {
                throw new IllegalArgumentException("plan task ids must be unique");
            }
        }
        for (PlanTask task : tasks) {
            if (task.dependsOn().contains(task.taskId())
                    || task.dependsOn().stream()
                    .anyMatch(dependencyTaskId -> !ids.contains(dependencyTaskId))) {
                throw new IllegalArgumentException("plan task dependencies are invalid");
            }
        }
        if (hasCycle(tasks, ids)) {
            throw new IllegalArgumentException("plan task dependencies must not contain a cycle");
        }
    }

    @Override
    public String actionType() {
        return StandardActionTypes.TASK_PLAN;
    }

    private static boolean hasCycle(List<PlanTask> tasks, Set<String> ids) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String taskId : ids) {
            if (visit(taskId, tasks, visited, visiting)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visit(String taskId, List<PlanTask> tasks,
                                 Set<String> visited, Set<String> visiting) {
        if (visited.contains(taskId)) {
            return false;
        }
        if (!visiting.add(taskId)) {
            return true;
        }
        PlanTask task = tasks.stream()
                .filter(item -> item.taskId().equals(taskId)).findFirst().orElseThrow();
        for (String dependencyTaskId : task.dependsOn()) {
            if (visit(dependencyTaskId, tasks, visited, visiting)) {
                return true;
            }
        }
        visiting.remove(taskId);
        visited.add(taskId);
        return false;
    }
}
