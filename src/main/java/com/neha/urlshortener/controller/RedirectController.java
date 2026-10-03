package com.neha.urlshortener.controller;

import com.neha.urlshortener.domain.ShortUrl;
import com.neha.urlshortener.service.UrlShortenerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;


@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlShortenerService service;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode) {

        ShortUrl shortUrl = service.getByShortCode(shortCode);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(shortUrl.getOriginalUrl()));

        return new ResponseEntity<>(
                headers,
                HttpStatus.FOUND
        );
    }
}