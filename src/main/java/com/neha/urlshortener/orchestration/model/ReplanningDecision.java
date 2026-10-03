package com.neha.urlshortener.orchestration.model;

public record ReplanningDecision(
        boolean replanned,
        String reason,
        String newPlan
) {
}