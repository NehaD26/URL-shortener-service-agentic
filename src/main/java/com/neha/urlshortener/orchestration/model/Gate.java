package com.neha.urlshortener.orchestration.model;

public record Gate(
        String name,
        String phase,
        String status,
        String reason
) {
}