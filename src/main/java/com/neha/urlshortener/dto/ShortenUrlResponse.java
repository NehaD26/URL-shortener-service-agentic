package com.neha.urlshortener.dto;

public record ShortenUrlResponse(
        String originalUrl,
        String shortCode,
        String shortUrl
) {
}