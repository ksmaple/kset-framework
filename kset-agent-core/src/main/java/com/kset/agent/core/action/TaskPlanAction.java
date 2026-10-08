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
            if (!ids.add(task.id())) {
                throw new IllegalArgumentException("plan task ids must be unique");
            }
        }
        for (PlanTask task : tasks) {
            if (task.dependencies().contains(task.id())
                    || task.dependencies().stream().anyMatch(dependency -> !ids.contains(dependency))) {
                throw new IllegalArgumentException("plan task dependencies are invalid");
            }
        }
        if (hasCycle(tasks, ids)) {
            throw new IllegalArgumentException("plan task dependencies must not contain a cycle");
        }
    }

    @Override
    public String type() {
        return StandardActionTypes.TASK_PLAN;
    }

    private static boolean hasCycle(List<PlanTask> tasks, Set<String> ids) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String id : ids) {
            if (visit(id, tasks, visited, visiting)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visit(String id, List<PlanTask> tasks,
                                 Set<String> visited, Set<String> visiting) {
        if (visited.contains(id)) {
            return false;
        }
        if (!visiting.add(id)) {
            return true;
        }
        PlanTask task = tasks.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
        for (String dependency : task.dependencies()) {
            if (visit(dependency, tasks, visited, visiting)) {
                return true;
            }
        }
        visiting.remove(id);
        visited.add(id);
        return false;
    }
}
