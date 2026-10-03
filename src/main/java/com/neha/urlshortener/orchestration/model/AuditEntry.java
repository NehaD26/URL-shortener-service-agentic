package com.neha.urlshortener.orchestration.model;

import java.time.LocalDateTime;

public record AuditEntry(
        LocalDateTime timestamp,
        String action,
        String status,
        String details
) {
}