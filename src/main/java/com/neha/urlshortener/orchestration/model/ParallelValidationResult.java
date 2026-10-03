package com.neha.urlshortener.orchestration.model;

public record ParallelValidationResult(
        boolean urlFormatValid,
        boolean policyValid,
        boolean synchronizedSuccessfully
) {
}