package com.neha.urlshortener.orchestration.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DependencyGraph {

    private final Map<String, List<String>> dependencies =
            new LinkedHashMap<>();

    public void addTask(String taskId, List<String> dependsOn) {
        dependencies.put(taskId, dependsOn);
    }

    public List<String> getDependencies(String taskId) {
        return dependencies.getOrDefault(taskId, List.of());
    }

    public boolean isReady(
            String taskId,
            List<String> completedTasks) {

        return completedTasks.containsAll(
                getDependencies(taskId)
        );
    }

    public Map<String, List<String>> snapshot() {
        return Map.copyOf(dependencies);
    }
}