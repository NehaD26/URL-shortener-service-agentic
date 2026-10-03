package com.neha.urlshortener.orchestration.model;

public record AgentStep(
        String action,
        String description,
        String status
) {
}