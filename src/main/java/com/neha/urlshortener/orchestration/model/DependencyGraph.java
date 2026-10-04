package com.neha.urlshortener.orchestration.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DependencyGraph {

    private final Map<String, List<String>> dependencies =
            new LinkedHashMap<>();

    public void addTask(
            String taskId,
            List<String> dependsOn) {

        dependencies.put(
                taskId,
                List.copyOf(dependsOn)
        );
    }

    public List<String> getDependencies(
            String taskId) {

        return dependencies.getOrDefault(
                taskId,
                List.of()
        );
    }

    /**
     * A task is ready only when every task it depends on
     * has completed successfully.
     */
    public boolean isReady(
            String taskId,
            List<String> completedTasks) {

        return completedTasks.containsAll(
                getDependencies(taskId)
        );
    }

    /**
     * Returns all tasks that are currently eligible to execute.
     *
     * This allows the orchestration service to use the dependency
     * graph as an execution mechanism rather than only documenting
     * dependencies after execution.
     */
    public List<String> getReadyTasks(
            List<String> completedTasks) {

        List<String> readyTasks =
                new ArrayList<>();

        for (String taskId : dependencies.keySet()) {

            if (!completedTasks.contains(taskId)
                    && isReady(taskId, completedTasks)) {

                readyTasks.add(taskId);
            }
        }

        return readyTasks;
    }

    /**
     * Used when an upstream SDLC artifact changes.
     * The orchestrator can determine which downstream tasks
     * depend on that output and need to be reconsidered.
     */
    public List<String> getDirectDependents(
            String taskId) {

        List<String> dependents =
                new ArrayList<>();

        for (Map.Entry<String, List<String>> entry
                : dependencies.entrySet()) {

            if (entry.getValue().contains(taskId)) {
                dependents.add(entry.getKey());
            }
        }

        return dependents;
    }

    public boolean containsTask(
            String taskId) {

        return dependencies.containsKey(taskId);
    }

    public Map<String, List<String>> snapshot() {

        return Map.copyOf(dependencies);
    }
}