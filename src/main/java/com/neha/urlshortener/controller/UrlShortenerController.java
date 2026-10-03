package com.neha.urlshortener.controller;

import com.neha.urlshortener.domain.ShortUrl;
import com.neha.urlshortener.dto.ShortenUrlRequest;
import com.neha.urlshortener.dto.ShortenUrlResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.neha.urlshortener.service.UrlShortenerService;
import com.neha.urlshortener.dto.AnalyticsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController

@RequiredArgsConstructor

@RequestMapping("/api/urls")
public class UrlShortenerController {

    private final UrlShortenerService service;

    @PostMapping
    public ResponseEntity<ShortenUrlResponse> shorten(
            @Valid @RequestBody ShortenUrlRequest request) {

        ShortUrl savedUrl =
                service.shortenUrl(request.url(), request.expirationMinutes());

        ShortenUrlResponse response = new ShortenUrlResponse(
                savedUrl.getOriginalUrl(),
                savedUrl.getShortCode(),
                "http://localhost:8080/" + savedUrl.getShortCode()
        );

        return ResponseEntity.ok(response);
    }
    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable String shortCode) {

        ShortUrl shortUrl = service.getAnalytics(shortCode);

        AnalyticsResponse response = new AnalyticsResponse(
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getClickCount(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt()
        );

        return ResponseEntity.ok(response);
    }

    @SpringBootApplication
    public static class AgenticSdlcUrlShortenerApplication {

        public static void main(String[] args) {
            SpringApplication.run(AgenticSdlcUrlShortenerApplication.class, args);
        }

    }
}