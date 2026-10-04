package com.neha.urlshortener.orchestration.model;

import java.time.LocalDateTime;

public record StageArtifact(
        SdlcStage stage,
        String name,
        String content,
        String status,
        LocalDateTime createdAt
) {
}