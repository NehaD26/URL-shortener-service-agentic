package com.neha.urlshortener.orchestration.model;

import java.util.List;

public record AgentResponse(
        String sessionId,
        String goal,
        ScenarioType scenarioType,
        RequirementAnalysis requirementAnalysis,
        WorkflowType workflowType,
        String status,
        String message,
        String shortUrl,
        boolean approvalRequired,
        ReliabilityMetrics reliabilityMetrics,
        List<AgentStep> steps,
        List<AgentTask> tasks,
        List<AuditEntry> auditTrail
) {
}