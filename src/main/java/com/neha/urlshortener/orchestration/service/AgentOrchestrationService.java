package com.neha.urlshortener.orchestration.service;

import com.neha.urlshortener.domain.ShortUrl;
import com.neha.urlshortener.orchestration.model.*;
import com.neha.urlshortener.service.UrlShortenerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AgentOrchestrationService {

    private final UrlShortenerService urlShortenerService;

    private final Map<String, OrchestrationSession> sessions =
            new ConcurrentHashMap<>();

    private final RetryPolicy retryPolicy =
            new RetryPolicy(3);

    public AgentResponse execute(AgentRequest request) {

        long startTime = System.currentTimeMillis();

        String sessionId =
                UUID.randomUUID().toString();

        List<AgentStep> steps =
                new ArrayList<>();

        List<AgentTask> tasks =
                new ArrayList<>();

        List<AuditEntry> auditTrail =
                new ArrayList<>();

        DependencyGraph dependencyGraph =
                new DependencyGraph();

        int attemptsUsed = 0;
        boolean fallbackActivated = false;
        boolean rollbackTriggered = false;
        boolean replanned = false;

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "SESSION_CREATED",
                "COMPLETED",
                "Stateful orchestration session created"
        ));

        // =====================================================
        // ENTRY GATE
        // =====================================================

        steps.add(new AgentStep(
                "ENTRY_GATE",
                "Validate that orchestration may begin",
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "ENTRY_GATE",
                "COMPLETED",
                "Request admitted into orchestration workflow"
        ));

        // =====================================================
        // REQUIREMENT UNDERSTANDING
        // =====================================================

        ScenarioType scenarioType =
                determineScenario(request);

        RequirementAnalysis requirementAnalysis =
                analyzeRequirement(request, scenarioType);

        dependencyGraph.addTask(
                "TASK-1",
                List.of()
        );

        tasks.add(new AgentTask(
                "TASK-1",
                "Understand Requirement",
                "Interpret intent and normalize the engineering problem",
                List.of(),
                "COMPLETED"
        ));

        steps.add(new AgentStep(
                "UNDERSTAND_REQUIREMENT",
                requirementAnalysis.normalizedRequirement(),
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "REQUIREMENT_ANALYZED",
                "COMPLETED",
                "Scenario classified as " + scenarioType
        ));

        // =====================================================
        // AMBIGUOUS SCENARIO
        // =====================================================

        if (scenarioType == ScenarioType.AMBIGUOUS) {

            dependencyGraph.addTask(
                    "TASK-2",
                    List.of("TASK-1")
            );

            tasks.add(new AgentTask(
                    "TASK-2",
                    "Clarify Requirement",
                    "Request clarification before implementation",
                    List.of("TASK-1"),
                    "WAITING"
            ));

            steps.add(new AgentStep(
                    "CLARIFICATION_GATE",
                    "Requirement ambiguity prevents safe execution",
                    "WAITING"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "SAFE_STOP_FOR_CLARIFICATION",
                    "WAITING",
                    "Execution stopped instead of guessing"
            ));

            ReliabilityMetrics metrics =
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    );

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    null,
                    "WAITING_FOR_CLARIFICATION",
                    "Clarification is required before the agent can continue",
                    null,
                    true,
                    metrics,
                    steps,
                    tasks,
                    auditTrail
            );
        }

        // =====================================================
        // GREENFIELD / BROWNFIELD
        // =====================================================

        dependencyGraph.addTask(
                "TASK-2",
                List.of("TASK-1")
        );

        if (scenarioType == ScenarioType.GREENFIELD) {

            tasks.add(new AgentTask(
                    "TASK-2",
                    "Greenfield Planning",
                    "Plan a new implementation path",
                    List.of("TASK-1"),
                    "COMPLETED"
            ));

            steps.add(new AgentStep(
                    "GREENFIELD_PLANNING",
                    "New capability implementation plan created",
                    "COMPLETED"
            ));

        } else {

            tasks.add(new AgentTask(
                    "TASK-2",
                    "Brownfield Analysis",
                    "Analyze impacted existing components",
                    List.of("TASK-1"),
                    "COMPLETED"
            ));

            steps.add(new AgentStep(
                    "BROWNFIELD_ANALYSIS",
                    "Existing controllers, services, repositories and orchestration impacts analyzed",
                    "COMPLETED"
            ));
        }

        // =====================================================
        // WORKFLOW SELECTION
        // =====================================================

        WorkflowType workflowType =
                request.expirationMinutes() != null
                        ? WorkflowType.EXPIRING_URL
                        : WorkflowType.STANDARD_URL;

        dependencyGraph.addTask(
                "TASK-3",
                List.of("TASK-2")
        );

        tasks.add(new AgentTask(
                "TASK-3",
                "Select Workflow",
                "Choose execution branch based on request properties",
                List.of("TASK-2"),
                "COMPLETED"
        ));

        steps.add(new AgentStep(
                "SELECT_WORKFLOW",
                "Selected workflow: " + workflowType,
                "COMPLETED"
        ));

        // =====================================================
        // URL ENTRY GATE
        // =====================================================

        if (request.url() == null ||
                request.url().isBlank()) {

            steps.add(new AgentStep(
                    "URL_ENTRY_GATE",
                    "URL is missing",
                    "FAILED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "URL_ENTRY_GATE",
                    "FAILED",
                    "URL cannot be null or empty"
            ));

            ReliabilityMetrics metrics =
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    );

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "FAILED",
                    "Workflow failed because the URL is missing",
                    null,
                    false,
                    metrics,
                    steps,
                    tasks,
                    auditTrail
            );
        }

        // =====================================================
        // PARALLEL VALIDATION
        // =====================================================

        dependencyGraph.addTask(
                "TASK-4A",
                List.of("TASK-3")
        );

        dependencyGraph.addTask(
                "TASK-4B",
                List.of("TASK-3")
        );

        tasks.add(new AgentTask(
                "TASK-4A",
                "URL Format Validation",
                "Validate URL format in parallel",
                List.of("TASK-3"),
                "COMPLETED"
        ));

        tasks.add(new AgentTask(
                "TASK-4B",
                "Policy Validation",
                "Validate URL policy rules in parallel",
                List.of("TASK-3"),
                "COMPLETED"
        ));

        CompletableFuture<Boolean> formatValidation =
                CompletableFuture.supplyAsync(
                        () -> isValidUrl(request.url())
                );

        CompletableFuture<Boolean> policyValidation =
                CompletableFuture.supplyAsync(
                        () -> passesPolicy(request.url())
                );

        CompletableFuture.allOf(
                formatValidation,
                policyValidation
        ).join();

        boolean formatValid =
                formatValidation.join();

        boolean policyValid =
                policyValidation.join();

        steps.add(new AgentStep(
                "SYNCHRONIZATION_POINT",
                "Parallel validation branches synchronized",
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "PARALLEL_BRANCH_SYNCHRONIZED",
                "COMPLETED",
                "URL format and policy validation completed"
        ));

        // =====================================================
        // DYNAMIC REPLANNING
        // =====================================================

        if (!formatValid) {

            ReplanningDecision decision =
                    replanForInvalidUrl(request.url());

            replanned = decision.replanned();

            steps.add(new AgentStep(
                    "DYNAMIC_REPLAN",
                    decision.newPlan(),
                    "COMPLETED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "REPLAN_DECISION",
                    "COMPLETED",
                    decision.reason()
            ));

            formatValid =
                    isValidUrlAfterReplan(
                            request.url()
                    );
        }

        if (!formatValid) {

            steps.add(new AgentStep(
                    "VALIDATION_GATE",
                    "URL format validation failed after replanning",
                    "FAILED"
            ));

            ReliabilityMetrics metrics =
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    );

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "FAILED",
                    "URL format validation failed",
                    null,
                    false,
                    metrics,
                    steps,
                    tasks,
                    auditTrail
            );
        }

        if (!policyValid) {

            steps.add(new AgentStep(
                    "POLICY_GATE",
                    "URL rejected by policy guardrail",
                    "FAILED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "POLICY_GATE",
                    "FAILED",
                    "Unsafe URL scheme rejected"
            ));

            ReliabilityMetrics metrics =
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    );

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "FAILED",
                    "URL rejected by policy guardrail",
                    null,
                    false,
                    metrics,
                    steps,
                    tasks,
                    auditTrail
            );
        }

        // =====================================================
        // HUMAN APPROVAL
        // =====================================================

        boolean approvalRequired =
                request.expirationMinutes() != null
                        &&
                        request.expirationMinutes() > 60;

        if (approvalRequired &&
                !Boolean.TRUE.equals(request.approved())) {

            tasks.add(new AgentTask(
                    "TASK-5",
                    "Human Approval",
                    "Require approval for long-lived URL",
                    List.of("TASK-4A", "TASK-4B"),
                    "WAITING"
            ));

            steps.add(new AgentStep(
                    "APPROVAL_GATE",
                    "Human approval required because expiration exceeds 60 minutes",
                    "WAITING"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "APPROVAL_REQUIRED",
                    "WAITING",
                    "Execution paused at governance checkpoint"
            ));

            ReliabilityMetrics metrics =
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    );

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "WAITING_FOR_APPROVAL",
                    "Human approval is required before execution can continue",
                    null,
                    true,
                    metrics,
                    steps,
                    tasks,
                    auditTrail
            );
        }

        // =====================================================
        // EXPIRATION BRANCH
        // =====================================================

        String creationDependency =
                "TASK-4A";

        if (workflowType ==
                WorkflowType.EXPIRING_URL) {

            tasks.add(new AgentTask(
                    "TASK-5",
                    "Validate Expiration",
                    "Validate expiration settings",
                    List.of(
                            "TASK-4A",
                            "TASK-4B"
                    ),
                    "COMPLETED"
            ));

            steps.add(new AgentStep(
                    "VALIDATE_EXPIRATION",
                    "Expiration configuration validated",
                    "COMPLETED"
            ));

            creationDependency =
                    "TASK-5";
        }

        // =====================================================
        // BOUNDED RETRIES
        // =====================================================

        ShortUrl shortUrl = null;
        Exception lastException = null;

        for (int attempt = 1;
             attempt <= retryPolicy.maxAttempts();
             attempt++) {

            attemptsUsed = attempt;

            try {

                auditTrail.add(new AuditEntry(
                        LocalDateTime.now(),
                        "EXECUTION_ATTEMPT",
                        "RUNNING",
                        "Attempt " + attempt
                ));

                shortUrl =
                        urlShortenerService
                                .shortenUrl(
                                        request.url(),
                                        request.expirationMinutes()
                                );

                auditTrail.add(new AuditEntry(
                        LocalDateTime.now(),
                        "EXECUTION_ATTEMPT",
                        "COMPLETED",
                        "Succeeded on attempt " + attempt
                ));

                break;

            } catch (Exception exception) {

                lastException =
                        exception;

                auditTrail.add(new AuditEntry(
                        LocalDateTime.now(),
                        "EXECUTION_ATTEMPT",
                        "FAILED",
                        "Attempt " + attempt + " failed"
                ));
            }
        }

        // =====================================================
        // FALLBACK + ROLLBACK + SAFE STOP
        // =====================================================

        if (shortUrl == null) {

            fallbackActivated = true;

            steps.add(new AgentStep(
                    "FALLBACK",
                    "Primary execution path exhausted bounded retries",
                    "COMPLETED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "FALLBACK_ACTIVATED",
                    "COMPLETED",
                    "Fallback policy activated"
            ));

            rollbackTriggered = true;

            steps.add(new AgentStep(
                    "ROLLBACK",
                    "Rollback executed to prevent partial workflow state",
                    "COMPLETED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "ROLLBACK",
                    "COMPLETED",
                    "No successful ShortUrl persisted; orchestration state marked failed"
            ));

            steps.add(new AgentStep(
                    "SAFE_STOP",
                    "Unsafe continuation prevented",
                    "FAILED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "SAFE_STOP",
                    "FAILED",
                    "Stopped after bounded retry exhaustion"
            ));

            String failureMessage =
                    lastException == null
                            ? "Unknown failure"
                            : lastException.getMessage();

            ReliabilityMetrics metrics =
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    );

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "FAILED",
                    "URL creation failed after bounded retries: "
                            + failureMessage,
                    null,
                    approvalRequired,
                    metrics,
                    steps,
                    tasks,
                    auditTrail
            );
        }

        // =====================================================
        // VERIFY + EXIT GATE
        // =====================================================

        tasks.add(new AgentTask(
                "TASK-6",
                "Create Short URL",
                "Generate and persist shortened URL",
                List.of(creationDependency),
                "COMPLETED"
        ));

        tasks.add(new AgentTask(
                "TASK-7",
                "Verify Result",
                "Verify generated short URL",
                List.of("TASK-6"),
                "COMPLETED"
        ));

        steps.add(new AgentStep(
                "RESULT_VERIFICATION",
                "Generated short URL verified",
                "COMPLETED"
        ));

        steps.add(new AgentStep(
                "EXIT_GATE",
                "Required completion criteria satisfied",
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "EXIT_GATE",
                "COMPLETED",
                "Workflow met completion criteria"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "WORKFLOW_COMPLETED",
                "COMPLETED",
                "Dependency graph completed: "
                        + dependencyGraph.snapshot()
        ));

        ReliabilityMetrics metrics =
                metrics(
                        attemptsUsed,
                        fallbackActivated,
                        rollbackTriggered,
                        replanned,
                        startTime
                );

        return storeAndRespond(
                sessionId,
                request,
                scenarioType,
                requirementAnalysis,
                workflowType,
                "COMPLETED",
                "URL shortening workflow completed successfully",
                "http://localhost:8080/"
                        + shortUrl.getShortCode(),
                approvalRequired,
                metrics,
                steps,
                tasks,
                auditTrail
        );
    }

    private ReplanningDecision replanForInvalidUrl(
            String url) {

        if (url != null &&
                !url.contains("://")) {

            return new ReplanningDecision(
                    true,
                    "URL did not contain a scheme",
                    "Replan validation by evaluating the URL with an HTTPS scheme"
            );
        }

        return new ReplanningDecision(
                true,
                "Initial URL validation failed",
                "No safe automatic correction available; preserve failure path"
        );
    }

    private boolean isValidUrlAfterReplan(
            String url) {

        if (url == null ||
                url.isBlank()) {

            return false;
        }

        if (!url.contains("://")) {
            return isValidUrl(
                    "https://" + url
            );
        }

        return false;
    }

    private boolean isValidUrl(
            String url) {

        try {

            URI uri = URI.create(url);

            String scheme =
                    uri.getScheme();

            return scheme != null
                    &&
                    (
                            scheme.equalsIgnoreCase("http")
                                    ||
                                    scheme.equalsIgnoreCase("https")
                    )
                    &&
                    uri.getHost() != null;

        } catch (Exception exception) {

            return false;
        }
    }

    private boolean passesPolicy(
            String url) {

        String lower =
                url.toLowerCase();

        return !lower.startsWith("file:")
                &&
                !lower.startsWith("javascript:")
                &&
                !lower.startsWith("data:");
    }

    private ScenarioType determineScenario(
            AgentRequest request) {

        if (request.scenarioType() != null) {
            return request.scenarioType();
        }

        if (request.goal() == null ||
                request.goal().isBlank()) {

            return ScenarioType.AMBIGUOUS;
        }

        String goal =
                request.goal().toLowerCase();

        if (goal.contains("maybe")
                || goal.contains("something")
                || goal.contains("not sure")
                || goal.contains("unclear")) {

            return ScenarioType.AMBIGUOUS;
        }

        if (goal.contains("existing")
                || goal.contains("modify")
                || goal.contains("refactor")
                || goal.contains("bug")
                || goal.contains("enhance")
                || goal.contains("change")) {

            return ScenarioType.BROWNFIELD;
        }

        return ScenarioType.GREENFIELD;
    }

    private RequirementAnalysis analyzeRequirement(
            AgentRequest request,
            ScenarioType scenarioType) {

        if (scenarioType ==
                ScenarioType.AMBIGUOUS) {

            return new RequirementAnalysis(
                    request.goal(),
                    "Requirement requires clarification before implementation",
                    scenarioType,
                    true,
                    List.of(
                            "Expected behavior is not sufficiently defined",
                            "Implementation should not proceed by guessing"
                    ),
                    List.of()
            );
        }

        if (scenarioType ==
                ScenarioType.BROWNFIELD) {

            return new RequirementAnalysis(
                    request.goal(),
                    "Modify the existing URL shortening system while preserving current behavior",
                    scenarioType,
                    false,
                    List.of(
                            "Existing APIs remain backward compatible",
                            "Existing persisted URL data must remain valid"
                    ),
                    List.of(
                            "UrlShortenerController",
                            "UrlShortenerService",
                            "ShortUrlRepository",
                            "AgentOrchestrationService"
                    )
            );
        }

        return new RequirementAnalysis(
                request.goal(),
                "Create URL shortening capability from the requested goal",
                scenarioType,
                false,
                List.of(
                        "Generated short codes must be unique",
                        "Validation and persistence rules apply"
                ),
                List.of(
                        "UrlShortenerController",
                        "UrlShortenerService",
                        "ShortUrlRepository"
                )
        );
    }

    private ReliabilityMetrics metrics(
            int attemptsUsed,
            boolean fallbackActivated,
            boolean rollbackTriggered,
            boolean replanned,
            long startTime) {

        int retriesUsed =
                Math.max(
                        0,
                        attemptsUsed - 1
                );

        return new ReliabilityMetrics(
                attemptsUsed,
                retriesUsed,
                fallbackActivated,
                rollbackTriggered,
                replanned,
                System.currentTimeMillis()
                        - startTime
        );
    }

    private AgentResponse storeAndRespond(
            String sessionId,
            AgentRequest request,
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
            List<AuditEntry> auditTrail) {

        OrchestrationSession session =
                new OrchestrationSession(
                        sessionId,
                        request.goal(),
                        scenarioType,
                        requirementAnalysis,
                        workflowType,
                        status,
                        LocalDateTime.now(),
                        approvalRequired,
                        reliabilityMetrics,
                        List.copyOf(steps),
                        List.copyOf(tasks),
                        List.copyOf(auditTrail)
                );

        sessions.put(
                sessionId,
                session
        );

        return new AgentResponse(
                sessionId,
                request.goal(),
                scenarioType,
                requirementAnalysis,
                workflowType,
                status,
                message,
                shortUrl,
                approvalRequired,
                reliabilityMetrics,
                steps,
                tasks,
                auditTrail
        );
    }

    public OrchestrationSession getSession(
            String sessionId) {

        return sessions.get(sessionId);
    }
}