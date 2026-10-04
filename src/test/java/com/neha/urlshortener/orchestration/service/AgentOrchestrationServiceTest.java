package com.neha.urlshortener.orchestration.service;

import com.neha.urlshortener.domain.ShortUrl;
import com.neha.urlshortener.orchestration.model.AgentRequest;
import com.neha.urlshortener.orchestration.model.AgentResponse;
import com.neha.urlshortener.orchestration.model.ScenarioType;
import com.neha.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class AgentOrchestrationServiceTest {

    private UrlShortenerService urlShortenerService;
    private AgentOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        urlShortenerService =
                Mockito.mock(UrlShortenerService.class);

        orchestrationService =
                new AgentOrchestrationService(urlShortenerService);
    }

    @Test
    void shouldCreateStandardUrlWorkflow() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("abc1234")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                Mockito.isNull()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create a short URL",
                "https://www.google.com",
                null,
                null,
                null
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals("COMPLETED", response.status());

        assertEquals(
                "STANDARD_URL",
                response.workflowType().name()
        );

        assertEquals(
                ScenarioType.GREENFIELD,
                response.scenarioType()
        );

        assertEquals(
                "http://localhost:8080/abc1234",
                response.shortUrl()
        );

        assertNotNull(response.requirementAnalysis());
        assertNotNull(response.reliabilityMetrics());

        assertFalse(response.approvalRequired());
        assertFalse(response.steps().isEmpty());
        assertFalse(response.tasks().isEmpty());
        assertFalse(response.stageArtifacts().isEmpty());
        assertFalse(response.auditTrail().isEmpty());
    }

    @Test
    void shouldCreateExpiringUrlWorkflow() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("exp1234")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                anyInt()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create expiring URL",
                "https://www.google.com",
                30,
                null,
                null
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals("COMPLETED", response.status());

        assertEquals(
                "EXPIRING_URL",
                response.workflowType().name()
        );

        assertEquals(
                "http://localhost:8080/exp1234",
                response.shortUrl()
        );
    }

    @Test
    void shouldWaitForApprovalForLongExpiration() {

        AgentRequest request = new AgentRequest(
                "Create long-lived short URL",
                "https://www.google.com",
                120,
                null,
                null
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "WAITING_FOR_APPROVAL",
                response.status()
        );

        assertTrue(response.approvalRequired());
        assertNull(response.shortUrl());

        assertTrue(
                response.steps().stream()
                        .anyMatch(step ->
                                step.action()
                                        .equals("APPROVAL_GATE"))
        );
    }

    @Test
    void shouldExecuteApprovedLongExpirationWorkflow() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("approved1")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                anyInt()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create long-lived short URL",
                "https://www.google.com",
                120,
                true,
                null
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "COMPLETED",
                response.status()
        );

        assertTrue(response.approvalRequired());

        assertEquals(
                "http://localhost:8080/approved1",
                response.shortUrl()
        );
    }

    @Test
    void shouldFailWhenUrlIsMissing() {

        AgentRequest request = new AgentRequest(
                "Create short URL",
                "",
                null,
                null,
                null
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "FAILED",
                response.status()
        );

        assertNull(response.shortUrl());

        assertEquals(
                "Workflow failed because the URL is missing",
                response.message()
        );
    }

    @Test
    void shouldStoreSession() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("session1")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                Mockito.isNull()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create short URL",
                "https://www.google.com",
                null,
                null,
                null
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertNotNull(
                orchestrationService.getSession(
                        response.sessionId()
                )
        );

        assertEquals(
                "COMPLETED",
                orchestrationService
                        .getSession(response.sessionId())
                        .status()
        );

        assertFalse(
                orchestrationService
                        .getSession(response.sessionId())
                        .stageArtifacts()
                        .isEmpty()
        );
    }

    @Test
    void shouldHandleBrownfieldScenario() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("brown01")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                anyInt()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Enhance the existing URL shortener with expiration support",
                "https://www.google.com",
                30,
                null,
                ScenarioType.BROWNFIELD
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "COMPLETED",
                response.status()
        );

        assertEquals(
                ScenarioType.BROWNFIELD,
                response.scenarioType()
        );

        assertFalse(
                response.requirementAnalysis()
                        .impactedComponents()
                        .isEmpty()
        );
    }

    @Test
    void shouldStopAmbiguousScenarioForClarification() {

        AgentRequest request = new AgentRequest(
                "Maybe change something in the URL system",
                "https://www.google.com",
                null,
                null,
                ScenarioType.AMBIGUOUS
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "WAITING_FOR_CLARIFICATION",
                response.status()
        );

        assertEquals(
                ScenarioType.AMBIGUOUS,
                response.scenarioType()
        );

        assertTrue(
                response.requirementAnalysis()
                        .ambiguous()
        );

        assertNull(response.shortUrl());

        assertTrue(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("REQUIREMENTS"))
        );

        assertFalse(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("DESIGN"))
        );
    }

    @Test
    void shouldRetryAfterTemporaryFailure() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("retry01")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                Mockito.isNull()
        ))
                .thenThrow(
                        new RuntimeException(
                                "Temporary database failure"
                        )
                )
                .thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create short URL",
                "https://www.google.com",
                null,
                null,
                ScenarioType.GREENFIELD
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "COMPLETED",
                response.status()
        );

        assertEquals(
                2,
                response.reliabilityMetrics()
                        .attemptsUsed()
        );

        assertEquals(
                1,
                response.reliabilityMetrics()
                        .retriesUsed()
        );

        assertFalse(
                response.reliabilityMetrics()
                        .fallbackActivated()
        );
    }

    @Test
    void shouldFallbackRollbackAndSafeStopAfterRetryExhaustion() {

        when(urlShortenerService.shortenUrl(
                any(),
                Mockito.isNull()
        )).thenThrow(
                new RuntimeException(
                        "Database unavailable"
                )
        );

        AgentRequest request = new AgentRequest(
                "Create short URL",
                "https://www.google.com",
                null,
                null,
                ScenarioType.GREENFIELD
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "FAILED",
                response.status()
        );

        assertEquals(
                3,
                response.reliabilityMetrics()
                        .attemptsUsed()
        );

        assertEquals(
                2,
                response.reliabilityMetrics()
                        .retriesUsed()
        );

        assertTrue(
                response.reliabilityMetrics()
                        .fallbackActivated()
        );

        assertTrue(
                response.reliabilityMetrics()
                        .rollbackTriggered()
        );

        assertTrue(
                response.steps().stream()
                        .anyMatch(step ->
                                step.action()
                                        .equals("FALLBACK"))
        );

        assertTrue(
                response.steps().stream()
                        .anyMatch(step ->
                                step.action()
                                        .equals("ROLLBACK"))
        );

        assertTrue(
                response.steps().stream()
                        .anyMatch(step ->
                                step.action()
                                        .equals("SAFE_STOP"))
        );
    }

    @Test
    void shouldDynamicallyReplanUrlWithoutScheme() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("replan1")
                .originalUrl("https://google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                Mockito.eq("https://google.com"),
                Mockito.isNull()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create short URL",
                "google.com",
                null,
                null,
                ScenarioType.GREENFIELD
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "COMPLETED",
                response.status()
        );

        assertEquals(
                "http://localhost:8080/replan1",
                response.shortUrl()
        );

        assertTrue(
                response.reliabilityMetrics()
                        .replanned()
        );

        assertTrue(
                response.steps().stream()
                        .anyMatch(step ->
                                step.action()
                                        .equals("DYNAMIC_REPLAN"))
        );

        assertTrue(
                response.auditTrail().stream()
                        .anyMatch(entry ->
                                entry.action()
                                        .equals("UPSTREAM_OUTPUT_CHANGED"))
        );

        assertTrue(
                response.auditTrail().stream()
                        .anyMatch(entry ->
                                entry.action()
                                        .equals("DOWNSTREAM_REPLAN"))
        );

        Mockito.verify(
                urlShortenerService
        ).shortenUrl(
                Mockito.eq("https://google.com"),
                Mockito.isNull()
        );
    }

    @Test
    void shouldExposeExecutionLatencyMetric() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("metric1")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                Mockito.isNull()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create short URL",
                "https://www.google.com",
                null,
                null,
                ScenarioType.GREENFIELD
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertNotNull(
                response.reliabilityMetrics()
        );

        assertTrue(
                response.reliabilityMetrics()
                        .executionTimeMs() >= 0
        );

        assertEquals(
                1,
                response.reliabilityMetrics()
                        .attemptsUsed()
        );

        assertEquals(
                0,
                response.reliabilityMetrics()
                        .retriesUsed()
        );
    }

    @Test
    void shouldExecuteCompleteSdlcLifecycle() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("sdlc001")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                Mockito.isNull()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create a production-ready short URL",
                "https://www.google.com",
                null,
                null,
                ScenarioType.GREENFIELD
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "COMPLETED",
                response.status()
        );

        assertEquals(
                7,
                response.stageArtifacts().size()
        );

        assertTrue(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("REQUIREMENTS"))
        );

        assertTrue(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("DESIGN"))
        );

        assertTrue(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("IMPLEMENTATION"))
        );

        assertTrue(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("TEST_PLAN"))
        );

        assertTrue(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("TESTING"))
        );

        assertTrue(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("DOCUMENTATION"))
        );

        assertTrue(
                response.stageArtifacts().stream()
                        .anyMatch(artifact ->
                                artifact.stage().name()
                                        .equals("RELEASE_READINESS"))
        );
    }

    @Test
    void shouldSynchronizeParallelBranchesBeforeTesting() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("parallel1")
                .originalUrl("https://www.google.com")
                .clickCount(0L)
                .build();

        when(urlShortenerService.shortenUrl(
                any(),
                Mockito.isNull()
        )).thenReturn(shortUrl);

        AgentRequest request = new AgentRequest(
                "Create short URL with governed SDLC execution",
                "https://www.google.com",
                null,
                null,
                ScenarioType.GREENFIELD
        );

        AgentResponse response =
                orchestrationService.execute(request);

        assertEquals(
                "COMPLETED",
                response.status()
        );

        assertTrue(
                response.steps().stream()
                        .anyMatch(step ->
                                step.action()
                                        .equals("PARALLEL_SDLC_BRANCH"))
        );

        assertTrue(
                response.steps().stream()
                        .anyMatch(step ->
                                step.action()
                                        .equals("SYNCHRONIZATION_POINT"))
        );

        assertTrue(
                response.auditTrail().stream()
                        .anyMatch(entry ->
                                entry.action()
                                        .equals("PARALLEL_BRANCH_SYNCHRONIZED"))
        );
    }
}