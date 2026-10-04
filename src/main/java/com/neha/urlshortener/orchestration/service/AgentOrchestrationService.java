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

        String sessionId = UUID.randomUUID().toString();

        List<AgentStep> steps = new ArrayList<>();
        List<AgentTask> tasks = new ArrayList<>();
        List<StageArtifact> stageArtifacts = new ArrayList<>();
        List<AuditEntry> auditTrail = new ArrayList<>();
        List<String> completedTasks = new ArrayList<>();

        DependencyGraph dependencyGraph =
                buildSdlcDependencyGraph();

        int attemptsUsed = 0;
        boolean fallbackActivated = false;
        boolean rollbackTriggered = false;
        boolean replanned = false;

        String effectiveUrl = request.url();

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "SESSION_CREATED",
                "COMPLETED",
                "Stateful SDLC orchestration session created"
        ));

        // =====================================================
        // ENTRY GATE
        // =====================================================

        if (request.goal() == null ||
                request.goal().isBlank()) {

            steps.add(new AgentStep(
                    "ENTRY_GATE",
                    "Engineering goal is required before SDLC execution",
                    "FAILED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "ENTRY_GATE",
                    "FAILED",
                    "Workflow rejected because the engineering goal is missing"
            ));

            return storeAndRespond(
                    sessionId,
                    request,
                    ScenarioType.AMBIGUOUS,
                    null,
                    null,
                    "FAILED",
                    "Engineering goal is required",
                    null,
                    false,
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        steps.add(new AgentStep(
                "ENTRY_GATE",
                "Initial request admitted into the governed SDLC workflow",
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "ENTRY_GATE",
                "COMPLETED",
                "Request admitted into orchestration"
        ));

        // =====================================================
        // STAGE 1 — REQUIREMENTS
        // =====================================================

        ScenarioType scenarioType =
                determineScenario(request);

        RequirementAnalysis requirementAnalysis =
                analyzeRequirement(request, scenarioType);

        if (!dependencyGraph.isReady(
                "REQUIREMENTS",
                completedTasks)) {

            throw new IllegalStateException(
                    "Requirements stage dependencies are not satisfied"
            );
        }

        tasks.add(new AgentTask(
                "REQUIREMENTS",
                "Requirements Analysis",
                "Interpret intent, identify ambiguity, assumptions and impacted components",
                dependencyGraph.getDependencies("REQUIREMENTS"),
                "COMPLETED"
        ));

        steps.add(new AgentStep(
                "REQUIREMENTS",
                requirementAnalysis.normalizedRequirement(),
                "COMPLETED"
        ));

        stageArtifacts.add(new StageArtifact(
                SdlcStage.REQUIREMENTS,
                "Requirements Specification",
                buildRequirementsArtifact(requirementAnalysis),
                "COMPLETED",
                LocalDateTime.now()
        ));

        completedTasks.add("REQUIREMENTS");

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "REQUIREMENTS_COMPLETED",
                "COMPLETED",
                "Requirements artifact produced; scenario=" + scenarioType
        ));

        // =====================================================
        // AMBIGUOUS REQUIREMENT SAFE STOP
        // =====================================================

        if (scenarioType == ScenarioType.AMBIGUOUS) {

            tasks.add(new AgentTask(
                    "CLARIFICATION",
                    "Human Clarification",
                    "Resolve requirement ambiguity before architecture and implementation",
                    List.of("REQUIREMENTS"),
                    "WAITING"
            ));

            steps.add(new AgentStep(
                    "CLARIFICATION_GATE",
                    "Requirement ambiguity prevents safe downstream execution",
                    "WAITING"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "SAFE_STOP_FOR_CLARIFICATION",
                    "WAITING",
                    "Architecture and implementation were not started because requirements are ambiguous"
            ));

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    null,
                    "WAITING_FOR_CLARIFICATION",
                    "Clarification is required before the SDLC workflow can continue",
                    null,
                    true,
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        // =====================================================
        // STAGE 2 — DESIGN
        // =====================================================

        requireReady(
                dependencyGraph,
                "DESIGN",
                completedTasks
        );

        String designContent =
                buildDesignArtifact(
                        scenarioType,
                        requirementAnalysis
                );

        tasks.add(new AgentTask(
                "DESIGN",
                "Architecture and Design",
                "Produce architecture decisions and identify system impacts",
                dependencyGraph.getDependencies("DESIGN"),
                "COMPLETED"
        ));

        steps.add(new AgentStep(
                "DESIGN",
                "Architecture and design artifact produced",
                "COMPLETED"
        ));

        stageArtifacts.add(new StageArtifact(
                SdlcStage.DESIGN,
                "Architecture Design",
                designContent,
                "COMPLETED",
                LocalDateTime.now()
        ));

        completedTasks.add("DESIGN");

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "DESIGN_COMPLETED",
                "COMPLETED",
                "Architecture/design completed after requirements gate"
        ));

        // =====================================================
        // WORKFLOW SELECTION
        // =====================================================

        WorkflowType workflowType =
                request.expirationMinutes() != null
                        ? WorkflowType.EXPIRING_URL
                        : WorkflowType.STANDARD_URL;

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "WORKFLOW_SELECTED",
                "COMPLETED",
                "Selected workflow: " + workflowType
        ));

        // =====================================================
        // URL INPUT GATE
        // =====================================================

        if (effectiveUrl == null ||
                effectiveUrl.isBlank()) {

            steps.add(new AgentStep(
                    "URL_ENTRY_GATE",
                    "URL is missing",
                    "FAILED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "URL_ENTRY_GATE",
                    "FAILED",
                    "Implementation cannot proceed without a URL"
            ));

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
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        // =====================================================
        // PARALLEL SDLC BRANCH
        //
        // DESIGN
        //   |---- IMPLEMENTATION
        //   |---- TEST_PLAN
        //
        // Both become ready after DESIGN.
        // =====================================================

        List<String> readyAfterDesign =
                dependencyGraph.getReadyTasks(completedTasks);

        if (!readyAfterDesign.contains("IMPLEMENTATION") ||
                !readyAfterDesign.contains("TEST_PLAN")) {

            throw new IllegalStateException(
                    "Expected implementation and test-plan branches to be ready after design"
            );
        }

        final String implementationUrl = effectiveUrl;

        CompletableFuture<String> implementationPlanning =
                CompletableFuture.supplyAsync(
                        () -> buildImplementationArtifact(
                                scenarioType,
                                implementationUrl,
                                workflowType
                        )
                );

        CompletableFuture<String> testPlanning =
                CompletableFuture.supplyAsync(
                        () -> buildTestPlanArtifact(
                                workflowType
                        )
                );

        CompletableFuture.allOf(
                implementationPlanning,
                testPlanning
        ).join();

        String implementationArtifact =
                implementationPlanning.join();

        String testPlanArtifact =
                testPlanning.join();

        tasks.add(new AgentTask(
                "IMPLEMENTATION",
                "Implementation",
                "Prepare the implementation/change plan",
                dependencyGraph.getDependencies("IMPLEMENTATION"),
                "COMPLETED"
        ));

        stageArtifacts.add(new StageArtifact(
                SdlcStage.IMPLEMENTATION,
                "Implementation Plan",
                implementationArtifact,
                "COMPLETED",
                LocalDateTime.now()
        ));

        completedTasks.add("IMPLEMENTATION");

        tasks.add(new AgentTask(
                "TEST_PLAN",
                "Test Planning",
                "Prepare validation strategy in parallel with implementation planning",
                dependencyGraph.getDependencies("TEST_PLAN"),
                "COMPLETED"
        ));

        stageArtifacts.add(new StageArtifact(
                SdlcStage.TEST_PLAN,
                "Test Plan",
                testPlanArtifact,
                "COMPLETED",
                LocalDateTime.now()
        ));

        completedTasks.add("TEST_PLAN");

        steps.add(new AgentStep(
                "PARALLEL_SDLC_BRANCH",
                "Implementation planning and test planning executed in parallel",
                "COMPLETED"
        ));

        steps.add(new AgentStep(
                "SYNCHRONIZATION_POINT",
                "Implementation and test-plan branches synchronized before testing",
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "PARALLEL_BRANCH_SYNCHRONIZED",
                "COMPLETED",
                "IMPLEMENTATION and TEST_PLAN completed; TESTING is now eligible"
        ));

        // =====================================================
        // IMPLEMENTATION VALIDATION
        // =====================================================

        String validationUrl = effectiveUrl;

        CompletableFuture<Boolean> formatValidation =
                CompletableFuture.supplyAsync(
                        () -> isValidUrl(validationUrl)
                );

        CompletableFuture<Boolean> policyValidation =
                CompletableFuture.supplyAsync(
                        () -> passesPolicy(validationUrl)
                );

        CompletableFuture.allOf(
                formatValidation,
                policyValidation
        ).join();

        boolean formatValid =
                formatValidation.join();

        boolean policyValid =
                policyValidation.join();

        // =====================================================
        // DYNAMIC REPLANNING
        // =====================================================

        if (!formatValid) {

            ReplanningDecision decision =
                    replanForInvalidUrl(effectiveUrl);

            replanned = decision.replanned();

            if (replanned &&
                    effectiveUrl != null &&
                    !effectiveUrl.contains("://")) {

                String previousUrl = effectiveUrl;

                effectiveUrl =
                        "https://" + effectiveUrl;

                auditTrail.add(new AuditEntry(
                        LocalDateTime.now(),
                        "UPSTREAM_OUTPUT_CHANGED",
                        "COMPLETED",
                        "Execution input changed from "
                                + previousUrl
                                + " to "
                                + effectiveUrl
                ));

                auditTrail.add(new AuditEntry(
                        LocalDateTime.now(),
                        "DOWNSTREAM_REPLAN",
                        "COMPLETED",
                        "Implementation validation was recalculated after normalized input changed"
                ));
            }

            steps.add(new AgentStep(
                    "DYNAMIC_REPLAN",
                    decision.newPlan(),
                    replanned
                            ? "COMPLETED"
                            : "FAILED"
            ));

            formatValid =
                    isValidUrl(effectiveUrl);

            policyValid =
                    passesPolicy(effectiveUrl);
        }

        if (!formatValid) {

            steps.add(new AgentStep(
                    "VALIDATION_GATE",
                    "URL format validation failed after bounded replanning",
                    "FAILED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "VALIDATION_GATE",
                    "FAILED",
                    "No safe automatic URL correction was available"
            ));

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
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        if (!policyValid) {

            steps.add(new AgentStep(
                    "POLICY_GATE",
                    "URL rejected by security policy guardrail",
                    "FAILED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "POLICY_GATE",
                    "FAILED",
                    "Unsafe URL scheme rejected"
            ));

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
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        // =====================================================
        // CONTROLLED AUTONOMY / APPROVAL GATE
        // =====================================================

        boolean approvalRequired =
                request.expirationMinutes() != null
                        &&
                        request.expirationMinutes() > 60;

        if (approvalRequired &&
                !Boolean.TRUE.equals(request.approved())) {

            tasks.add(new AgentTask(
                    "APPROVAL",
                    "Human Approval",
                    "Require human approval for a high-impact long-lived URL",
                    List.of("IMPLEMENTATION", "TEST_PLAN"),
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
                    "Controlled autonomy boundary reached; execution paused"
            ));

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "WAITING_FOR_APPROVAL",
                    "Human approval is required before implementation execution can continue",
                    null,
                    true,
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        // =====================================================
        // EXECUTE IMPLEMENTATION WITH BOUNDED RETRIES
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
                        "IMPLEMENTATION_ATTEMPT",
                        "RUNNING",
                        "Attempt " + attempt
                ));

                shortUrl =
                        urlShortenerService.shortenUrl(
                                effectiveUrl,
                                request.expirationMinutes()
                        );

                auditTrail.add(new AuditEntry(
                        LocalDateTime.now(),
                        "IMPLEMENTATION_ATTEMPT",
                        "COMPLETED",
                        "Implementation execution succeeded on attempt "
                                + attempt
                ));

                break;

            } catch (Exception exception) {

                lastException = exception;

                auditTrail.add(new AuditEntry(
                        LocalDateTime.now(),
                        "IMPLEMENTATION_ATTEMPT",
                        "FAILED",
                        "Attempt " + attempt + " failed"
                ));
            }
        }

        // =====================================================
        // FALLBACK + COMPENSATING ROLLBACK + SAFE STOP
        // =====================================================

        if (shortUrl == null) {

            fallbackActivated = true;
            rollbackTriggered = true;

            steps.add(new AgentStep(
                    "FALLBACK",
                    "Primary implementation path exhausted bounded retries",
                    "COMPLETED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "FALLBACK_ACTIVATED",
                    "COMPLETED",
                    "Fallback policy activated after retry exhaustion"
            ));

            steps.add(new AgentStep(
                    "ROLLBACK",
                    "Compensating rollback marked the orchestration execution as failed",
                    "COMPLETED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "ROLLBACK",
                    "COMPLETED",
                    "No successful ShortUrl result was accepted; workflow state was not advanced"
            ));

            steps.add(new AgentStep(
                    "SAFE_STOP",
                    "Unsafe continuation into testing and release readiness prevented",
                    "FAILED"
            ));

            String failureMessage =
                    lastException == null
                            ? "Unknown failure"
                            : lastException.getMessage();

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "FAILED",
                    "Implementation failed after bounded retries: "
                            + failureMessage,
                    null,
                    approvalRequired,
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        // =====================================================
        // STAGE 5 — TESTING
        //
        // TESTING cannot execute until BOTH:
        // IMPLEMENTATION and TEST_PLAN are complete.
        // =====================================================

        requireReady(
                dependencyGraph,
                "TESTING",
                completedTasks
        );

        boolean persistedResultValid =
                shortUrl.getShortCode() != null
                        &&
                        !shortUrl.getShortCode().isBlank()
                        &&
                        shortUrl.getOriginalUrl() != null
                        &&
                        shortUrl.getOriginalUrl()
                                .equals(effectiveUrl);

        if (!persistedResultValid) {

            tasks.add(new AgentTask(
                    "TESTING",
                    "Testing and Validation",
                    "Validate implementation output against requirements",
                    dependencyGraph.getDependencies("TESTING"),
                    "FAILED"
            ));

            stageArtifacts.add(new StageArtifact(
                    SdlcStage.TESTING,
                    "Validation Result",
                    "Generated result failed post-implementation verification.",
                    "FAILED",
                    LocalDateTime.now()
            ));

            steps.add(new AgentStep(
                    "TESTING_GATE",
                    "Implementation output failed validation",
                    "FAILED"
            ));

            auditTrail.add(new AuditEntry(
                    LocalDateTime.now(),
                    "TESTING_FAILED",
                    "FAILED",
                    "Release pipeline stopped because implementation output did not satisfy validation"
            ));

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "FAILED",
                    "Implementation completed but validation failed",
                    null,
                    approvalRequired,
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        tasks.add(new AgentTask(
                "TESTING",
                "Testing and Validation",
                "Validate implementation output against requirements and test plan",
                dependencyGraph.getDependencies("TESTING"),
                "COMPLETED"
        ));

        stageArtifacts.add(new StageArtifact(
                SdlcStage.TESTING,
                "Validation Result",
                "URL format and policy checks passed; "
                        + "short URL was generated and persisted successfully; "
                        + "result verification passed.",
                "COMPLETED",
                LocalDateTime.now()
        ));

        completedTasks.add("TESTING");

        steps.add(new AgentStep(
                "TESTING",
                "Implementation output validated successfully",
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "TESTING_COMPLETED",
                "COMPLETED",
                "Testing stage passed after implementation and test-plan synchronization"
        ));

        // =====================================================
        // STAGE 6 — DOCUMENTATION
        // =====================================================

        requireReady(
                dependencyGraph,
                "DOCUMENTATION",
                completedTasks
        );

        tasks.add(new AgentTask(
                "DOCUMENTATION",
                "Documentation",
                "Produce reviewable implementation and operational documentation",
                dependencyGraph.getDependencies("DOCUMENTATION"),
                "COMPLETED"
        ));

        stageArtifacts.add(new StageArtifact(
                SdlcStage.DOCUMENTATION,
                "Documentation Summary",
                buildDocumentationArtifact(
                        scenarioType,
                        workflowType,
                        effectiveUrl
                ),
                "COMPLETED",
                LocalDateTime.now()
        ));

        completedTasks.add("DOCUMENTATION");

        steps.add(new AgentStep(
                "DOCUMENTATION",
                "Documentation artifact produced",
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "DOCUMENTATION_COMPLETED",
                "COMPLETED",
                "Reviewable documentation artifact generated"
        ));

        // =====================================================
        // STAGE 7 — RELEASE READINESS
        // =====================================================

        requireReady(
                dependencyGraph,
                "RELEASE_READINESS",
                completedTasks
        );

        boolean releaseReady =
                completedTasks.contains("REQUIREMENTS")
                        &&
                        completedTasks.contains("DESIGN")
                        &&
                        completedTasks.contains("IMPLEMENTATION")
                        &&
                        completedTasks.contains("TEST_PLAN")
                        &&
                        completedTasks.contains("TESTING")
                        &&
                        completedTasks.contains("DOCUMENTATION")
                        &&
                        persistedResultValid;

        if (!releaseReady) {

            tasks.add(new AgentTask(
                    "RELEASE_READINESS",
                    "Release Readiness",
                    "Evaluate final SDLC completion criteria",
                    dependencyGraph.getDependencies("RELEASE_READINESS"),
                    "FAILED"
            ));

            stageArtifacts.add(new StageArtifact(
                    SdlcStage.RELEASE_READINESS,
                    "Release Readiness Assessment",
                    "Release blocked because one or more required SDLC stages or validation gates failed.",
                    "FAILED",
                    LocalDateTime.now()
            ));

            steps.add(new AgentStep(
                    "EXIT_GATE",
                    "Release readiness criteria were not satisfied",
                    "FAILED"
            ));

            return storeAndRespond(
                    sessionId,
                    request,
                    scenarioType,
                    requirementAnalysis,
                    workflowType,
                    "FAILED",
                    "Release readiness gate failed",
                    null,
                    approvalRequired,
                    metrics(
                            attemptsUsed,
                            fallbackActivated,
                            rollbackTriggered,
                            replanned,
                            startTime
                    ),
                    steps,
                    tasks,
                    stageArtifacts,
                    auditTrail
            );
        }

        tasks.add(new AgentTask(
                "RELEASE_READINESS",
                "Release Readiness",
                "Evaluate final SDLC completion criteria",
                dependencyGraph.getDependencies("RELEASE_READINESS"),
                "COMPLETED"
        ));

        stageArtifacts.add(new StageArtifact(
                SdlcStage.RELEASE_READINESS,
                "Release Readiness Assessment",
                "Requirements, design, implementation, test planning, "
                        + "testing and documentation completed successfully. "
                        + "Required governance and validation gates passed.",
                "COMPLETED",
                LocalDateTime.now()
        ));

        completedTasks.add("RELEASE_READINESS");

        steps.add(new AgentStep(
                "EXIT_GATE",
                "Release readiness criteria satisfied",
                "COMPLETED"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "RELEASE_READINESS_COMPLETED",
                "COMPLETED",
                "Final SDLC exit gate passed"
        ));

        auditTrail.add(new AuditEntry(
                LocalDateTime.now(),
                "WORKFLOW_COMPLETED",
                "COMPLETED",
                "Completed dependency-driven SDLC graph: "
                        + dependencyGraph.snapshot()
        ));

        return storeAndRespond(
                sessionId,
                request,
                scenarioType,
                requirementAnalysis,
                workflowType,
                "COMPLETED",
                "Agentic SDLC workflow completed successfully and reached release readiness",
                "http://localhost:8080/"
                        + shortUrl.getShortCode(),
                approvalRequired,
                metrics(
                        attemptsUsed,
                        fallbackActivated,
                        rollbackTriggered,
                        replanned,
                        startTime
                ),
                steps,
                tasks,
                stageArtifacts,
                auditTrail
        );
    }

    // =========================================================
    // SDLC DEPENDENCY GRAPH
    // =========================================================

    private DependencyGraph buildSdlcDependencyGraph() {

        DependencyGraph graph =
                new DependencyGraph();

        graph.addTask(
                "REQUIREMENTS",
                List.of()
        );

        graph.addTask(
                "DESIGN",
                List.of("REQUIREMENTS")
        );

        /*
         * These two branches intentionally share DESIGN as
         * their dependency and can execute in parallel.
         */
        graph.addTask(
                "IMPLEMENTATION",
                List.of("DESIGN")
        );

        graph.addTask(
                "TEST_PLAN",
                List.of("DESIGN")
        );

        /*
         * Synchronization point:
         * testing requires BOTH parallel branches.
         */
        graph.addTask(
                "TESTING",
                List.of(
                        "IMPLEMENTATION",
                        "TEST_PLAN"
                )
        );

        graph.addTask(
                "DOCUMENTATION",
                List.of("TESTING")
        );

        graph.addTask(
                "RELEASE_READINESS",
                List.of("DOCUMENTATION")
        );

        return graph;
    }

    private void requireReady(
            DependencyGraph graph,
            String taskId,
            List<String> completedTasks) {

        if (!graph.isReady(
                taskId,
                completedTasks)) {

            throw new IllegalStateException(
                    "Task "
                            + taskId
                            + " cannot execute because dependencies are incomplete: "
                            + graph.getDependencies(taskId)
            );
        }
    }

    // =========================================================
    // REVIEWABLE SDLC ARTIFACTS
    // =========================================================

    private String buildRequirementsArtifact(
            RequirementAnalysis analysis) {

        return "Normalized requirement: "
                + analysis.normalizedRequirement()
                + ". Scenario: "
                + analysis.scenarioType()
                + ". Assumptions: "
                + analysis.assumptions()
                + ". Impacted components: "
                + analysis.impactedComponents();
    }

    private String buildDesignArtifact(
            ScenarioType scenarioType,
            RequirementAnalysis analysis) {

        if (scenarioType ==
                ScenarioType.BROWNFIELD) {

            return "Brownfield design: preserve existing API and persisted-data behavior; "
                    + "evaluate changes across "
                    + analysis.impactedComponents()
                    + "; maintain controller-service-repository separation; "
                    + "apply validation and governance before persistence.";
        }

        return "Greenfield design: REST controller -> service -> repository -> PostgreSQL; "
                + "agentic orchestration coordinates requirements, design, implementation, "
                + "testing, documentation and release-readiness stages; "
                + "short codes remain unique and redirects use persisted mappings.";
    }

    private String buildImplementationArtifact(
            ScenarioType scenarioType,
            String url,
            WorkflowType workflowType) {

        return "Implementation plan for "
                + scenarioType
                + ": validate and normalize input URL '"
                + url
                + "', apply policy guardrails, execute "
                + workflowType
                + " persistence through UrlShortenerService, "
                + "use bounded retries for execution failures, "
                + "and verify the persisted result before downstream release stages.";
    }

    private String buildTestPlanArtifact(
            WorkflowType workflowType) {

        return "Test plan: verify valid URL creation, invalid URL rejection, "
                + "policy enforcement, short-code generation, persistence, redirect behavior, "
                + "analytics behavior, retry/failure handling, dynamic replanning"
                + (workflowType == WorkflowType.EXPIRING_URL
                ? ", expiration behavior and approval controls."
                : ".");
    }

    private String buildDocumentationArtifact(
            ScenarioType scenarioType,
            WorkflowType workflowType,
            String effectiveUrl) {

        return "Documented scenario="
                + scenarioType
                + ", workflow="
                + workflowType
                + ", effective execution URL="
                + effectiveUrl
                + ". Include architecture, dependency graph, governance gates, "
                + "testing strategy, assumptions, limitations and release-readiness decision.";
    }

    // =========================================================
    // DYNAMIC REPLANNING
    // =========================================================

    private ReplanningDecision replanForInvalidUrl(
            String url) {

        if (url != null &&
                !url.contains("://")) {

            return new ReplanningDecision(
                    true,
                    "URL did not contain a scheme",
                    "Normalize the execution input with HTTPS and re-run dependent validation"
            );
        }

        return new ReplanningDecision(
                false,
                "Initial URL validation failed",
                "No safe automatic correction available; preserve failure path"
        );
    }

    // =========================================================
    // VALIDATION / POLICY
    // =========================================================

    private boolean isValidUrl(
            String url) {

        try {

            URI uri =
                    URI.create(url);

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

        if (url == null ||
                url.isBlank()) {

            return false;
        }

        String lower =
                url.toLowerCase();

        return !lower.startsWith("file:")
                &&
                !lower.startsWith("javascript:")
                &&
                !lower.startsWith("data:");
    }

    // =========================================================
    // SCENARIO / REQUIREMENT ANALYSIS
    // =========================================================

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

    // =========================================================
    // RELIABILITY METRICS
    // =========================================================

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

    // =========================================================
    // STATEFUL SESSION STORAGE / RESPONSE
    // =========================================================

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
            List<StageArtifact> stageArtifacts,
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
                        List.copyOf(stageArtifacts),
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
                List.copyOf(steps),
                List.copyOf(tasks),
                List.copyOf(stageArtifacts),
                List.copyOf(auditTrail)
        );
    }

    public OrchestrationSession getSession(
            String sessionId) {

        return sessions.get(sessionId);
    }
}