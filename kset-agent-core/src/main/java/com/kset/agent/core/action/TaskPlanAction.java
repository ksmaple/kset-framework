package com.kset.agent.core.action;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        Map<String, Integer> remainingDependencies = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (PlanTask task : tasks) {
            remainingDependencies.put(task.taskId(), task.dependsOn().size());
            for (String dependency : task.dependsOn()) {
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>())
                        .add(task.taskId());
            }
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (String taskId : ids) {
            if (remainingDependencies.get(taskId) == 0) {
                ready.add(taskId);
            }
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            String completed = ready.remove();
            visited++;
            for (String dependent : dependents.getOrDefault(completed, List.of())) {
                int remaining = remainingDependencies.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }
        return visited != tasks.size();
    }
}
