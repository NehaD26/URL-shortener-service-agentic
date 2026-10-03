package com.neha.urlshortener.orchestration.model;

import java.util.List;

public record AgentTask(
        String taskId,
        String name,
        String description,
        List<String> dependencies,
        String status
) {
}