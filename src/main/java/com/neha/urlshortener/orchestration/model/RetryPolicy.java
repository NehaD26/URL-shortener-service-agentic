package com.neha.urlshortener.orchestration.model;

public record RetryPolicy(
        int maxAttempts
) {
}