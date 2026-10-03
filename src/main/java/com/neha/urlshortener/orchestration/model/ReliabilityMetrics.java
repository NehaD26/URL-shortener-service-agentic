package com.neha.urlshortener.orchestration.model;

public record ReliabilityMetrics(
        int attemptsUsed,
        int retriesUsed,
        boolean fallbackActivated,
        boolean rollbackTriggered,
        boolean replanned,
        long executionTimeMs
) {
}