package com.neha.urlshortener.orchestration.model;

import java.time.LocalDateTime;
import java.util.List;

public record OrchestrationSession(
        String sessionId,
        String goal,
        ScenarioType scenarioType,
        RequirementAnalysis requirementAnalysis,
        WorkflowType workflowType,
        String status,
        LocalDateTime createdAt,
        boolean approvalRequired,
        ReliabilityMetrics reliabilityMetrics,
        List<AgentStep> steps,
        List<AgentTask> tasks,
        List<StageArtifact> stageArtifacts,
        List<AuditEntry> auditTrail
) {
}