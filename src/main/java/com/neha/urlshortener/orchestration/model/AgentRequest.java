package com.neha.urlshortener.orchestration.model;

public record AgentRequest(
        String goal,
        String url,
        Integer expirationMinutes,
        Boolean approved,
        ScenarioType scenarioType
) {
}