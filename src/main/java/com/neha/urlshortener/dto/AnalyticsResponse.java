package com.neha.urlshortener.dto;

import java.time.LocalDateTime;

public record AnalyticsResponse(
        String shortCode,
        String originalUrl,
        Long clickCount,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}