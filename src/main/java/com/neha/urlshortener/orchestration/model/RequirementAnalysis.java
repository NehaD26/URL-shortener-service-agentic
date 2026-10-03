package com.neha.urlshortener.orchestration.model;

import java.util.List;

public record RequirementAnalysis(
        String originalGoal,
        String normalizedRequirement,
        ScenarioType scenarioType,
        boolean ambiguous,
        List<String> assumptions,
        List<String> impactedComponents
) {
}