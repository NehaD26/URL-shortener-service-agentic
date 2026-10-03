package com.neha.urlshortener.orchestration.controller;

import com.neha.urlshortener.orchestration.model.AgentRequest;
import com.neha.urlshortener.orchestration.model.AgentResponse;
import com.neha.urlshortener.orchestration.model.OrchestrationSession;
import com.neha.urlshortener.orchestration.service.AgentOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrationService orchestrationService;

    @PostMapping("/execute")
    public ResponseEntity<AgentResponse> execute(
            @RequestBody AgentRequest request) {

        return ResponseEntity.ok(
                orchestrationService.execute(request)
        );
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<OrchestrationSession> getSession(
            @PathVariable String sessionId) {

        OrchestrationSession session =
                orchestrationService.getSession(sessionId);

        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(session);
    }
}