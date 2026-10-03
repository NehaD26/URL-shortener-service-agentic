package com.neha.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ShortenUrlRequest(

        @NotBlank(message = "URL is required")
        @Pattern(
                regexp = "^https?://.+",
                message = "URL must start with http:// or https://"
        )
        String url,

        @Positive(message = "Expiration minutes must be greater than 0")
        Integer expirationMinutes
) {
}