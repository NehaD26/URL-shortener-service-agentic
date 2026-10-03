package com.neha.urlshortener.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.neha.urlshortener.domain.ShortUrl;
import com.neha.urlshortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private static final String CHARACTERS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int CODE_LENGTH = 7;

    private final ShortUrlRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ShortUrl shortenUrl(String originalUrl, Integer expirationMinutes) {
        String shortCode = generateUniqueShortCode();

        java.time.LocalDateTime expiresAt = null;

        if (expirationMinutes != null) {
            expiresAt = java.time.LocalDateTime.now()
                    .plusMinutes(expirationMinutes);
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .originalUrl(originalUrl)
                .shortCode(shortCode)
                .expiresAt(expiresAt)
                .clickCount(0L)
                .build();

        return repository.save(shortUrl);
    }

    public ShortUrl getByShortCode(String shortCode) {
        ShortUrl shortUrl = repository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Short URL not found"
                        ));

        if (shortUrl.getExpiresAt() != null &&
                shortUrl.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Short URL has expired"
            );
        }

        shortUrl.setClickCount(shortUrl.getClickCount() + 1);
        return repository.save(shortUrl);
    }

    public ShortUrl getAnalytics(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Short URL not found"
                        ));
    }

    private String generateUniqueShortCode() {
        String code;

        do {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);

            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(
                        CHARACTERS.charAt(random.nextInt(CHARACTERS.length()))
                );
            }

            code = builder.toString();

        } while (repository.existsByShortCode(code));

        return code;
    }
}