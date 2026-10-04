# Agentic SDLC URL Shortener

A Java/Spring Boot URL shortener with a controlled, stateful SDLC orchestration layer.

The project demonstrates how an engineering requirement can move through a structured lifecycle:

```text
REQUIREMENTS
     |
     v
   DESIGN
     |
     +-------------------+
     |                   |
     v                   v
IMPLEMENTATION       TEST_PLAN
     |                   |
     +---------+---------+
               |
               v
            TESTING
               |
               v
        DOCUMENTATION
               |
               v
      RELEASE_READINESS
```

The URL shortener provides URL creation, redirects, expiration, analytics, and PostgreSQL persistence.

The orchestration layer adds requirement analysis, dependency-driven execution, parallel work, synchronization, validation and policy gates, human approval, bounded retries, dynamic replanning, fallback, rollback, safe-stop behavior, stage artifacts, audit history, and reliability metrics.

The orchestration is intentionally deterministic and bounded. The goal is not unrestricted autonomy. The goal is automation that is useful, explainable, testable, and controlled.

---

# What I Built

The project contains two main parts.

## 1. URL Shortener

A Spring Boot backend that can:

- Create short URLs
- Generate unique 7-character short codes
- Redirect short URLs to their original destinations
- Track click counts
- Return URL analytics
- Create URLs with optional expiration
- Reject expired URLs
- Persist mappings in PostgreSQL

## 2. Agentic SDLC Orchestration Layer

An orchestration API that demonstrates:

- Requirement understanding
- Greenfield, brownfield, and ambiguous scenarios
- Explicit SDLC stages
- Task decomposition
- Dependency-driven execution
- Entry and exit gates
- Parallel execution
- Synchronization points
- Validation and policy guardrails
- Human approval checkpoints
- Stateful workflow sessions
- Bounded retries
- Dynamic replanning
- Fallback behavior
- Orchestration-level rollback
- Safe-stop behavior
- Reviewable stage artifacts
- Decision and audit history
- Reliability metrics

---

# Tech Stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4 |
| API | Spring Web MVC / REST |
| Persistence | Spring Data JPA |
| Database | PostgreSQL |
| Infrastructure | Docker / Docker Compose |
| Build | Maven |
| Testing | JUnit 5, Mockito |
| Concurrency | CompletableFuture |
| Observability | Spring Boot Actuator |

---

# URL Shortener API

## Create a Short URL

```http
POST /api/urls
```

Example:

```json
{
  "url": "https://www.google.com"
}
```

With expiration:

```json
{
  "url": "https://www.google.com",
  "expirationMinutes": 30
}
```

`expirationMinutes` is optional.

The service generates a random 7-character alphanumeric short code and checks for an existing code before persistence.

## Redirect

```http
GET /{shortCode}
```

A valid short code redirects the caller to the original URL.

The redirect increments the URL's click count.

Expired URLs are rejected instead of being redirected.

## Analytics

```http
GET /api/urls/{shortCode}/analytics
```

Returns information about the shortened URL, including its click count, creation time, expiration time, and original URL.

Analytics retrieval itself does not increment the click count.

---

# SDLC Orchestration API

The main orchestration endpoint is:

```http
POST /api/agent/execute
```

Example:

```json
{
  "goal": "Create a new URL shortening capability",
  "url": "https://www.google.com",
  "scenarioType": "GREENFIELD"
}
```

The response exposes both the final outcome and the execution history.

It includes:

- Session ID
- Goal
- Scenario classification
- Requirement analysis
- Selected workflow
- Workflow status
- Approval requirement
- Generated short URL
- SDLC stage artifacts
- Task information
- Execution steps
- Audit history
- Reliability metrics

This makes the orchestration inspectable instead of treating it as a black box.

---

# Full SDLC Lifecycle

A major focus of the project is orchestration across multiple engineering stages rather than wrapping a single URL-creation call.

The lifecycle is:

```text
REQUIREMENTS
     |
     v
   DESIGN
     |
     +-------------------+
     |                   |
     v                   v
IMPLEMENTATION       TEST_PLAN
     |                   |
     +---------+---------+
               |
               v
            TESTING
               |
               v
        DOCUMENTATION
               |
               v
      RELEASE_READINESS
```

Each stage has explicit dependencies.

A stage is allowed to proceed only when the dependencies required by the `DependencyGraph` have completed.

This makes workflow readiness part of the orchestration model instead of relying only on Java statement ordering.

---

# Stage Artifacts

Each meaningful SDLC stage produces a `StageArtifact`.

A stage artifact contains:

```text
stage
name
content
status
createdAt
```

Artifacts are produced for:

```text
REQUIREMENTS
DESIGN
IMPLEMENTATION
TEST_PLAN
TESTING
DOCUMENTATION
RELEASE_READINESS
```

These artifacts make intermediate engineering outputs visible and reviewable.

A successful workflow therefore produces more than a shortened URL. It also provides evidence of how the requirement moved through the engineering lifecycle.

---

# Requirement Understanding

The first stage converts the incoming request into a `RequirementAnalysis`.

It captures:

- Original engineering goal
- Normalized requirement
- Scenario type
- Whether the requirement is ambiguous
- Assumptions
- Existing components potentially affected

The requirement analysis becomes the artifact for the `REQUIREMENTS` stage.

---

# Scenario Handling

The orchestration supports three scenarios.

## Greenfield

Example:

```json
{
  "goal": "Create a new URL shortening capability",
  "url": "https://www.google.com",
  "scenarioType": "GREENFIELD"
}
```

The request is treated as a new capability.

The orchestration generates requirement and design outputs before moving into implementation and test planning.

## Brownfield

Example:

```json
{
  "goal": "Enhance the existing URL shortener with expiration support",
  "url": "https://www.google.com",
  "expirationMinutes": 30,
  "scenarioType": "BROWNFIELD"
}
```

The system treats this as a change to an existing application.

The requirement analysis identifies potentially impacted components such as:

```text
UrlShortenerController
UrlShortenerService
ShortUrlRepository
AgentOrchestrationService
```

This impact analysis is intentionally deterministic for the assessment. It demonstrates brownfield reasoning without pretending to perform unrestricted autonomous source-code analysis.

## Ambiguous

Example:

```json
{
  "goal": "Maybe change something in the URL system",
  "url": "https://www.google.com",
  "scenarioType": "AMBIGUOUS"
}
```

The workflow does not guess what the user intended.

It produces the requirements artifact and transitions to:

```text
WAITING_FOR_CLARIFICATION
```

Downstream stages such as design and implementation are not executed.

This is an intentional autonomy boundary.

---

# Dependency-Driven Execution

The SDLC stages are represented in an explicit `DependencyGraph`.

Conceptually:

```text
REQUIREMENTS
    |
    v
DESIGN
    |
    +----------------+
    |                |
    v                v
IMPLEMENTATION    TEST_PLAN
    |                |
    +--------+-------+
             |
             v
          TESTING
             |
             v
      DOCUMENTATION
             |
             v
    RELEASE_READINESS
```

The orchestration checks whether a stage is ready based on completed dependencies.

For example:

```text
DESIGN
depends on:
REQUIREMENTS
```

```text
TESTING
depends on:
IMPLEMENTATION
TEST_PLAN
```

```text
RELEASE_READINESS
depends on:
DOCUMENTATION
```

The dependency graph can therefore determine which work is ready rather than serving only as documentation.

---

# Parallel Execution and Synchronization

The project demonstrates parallel execution in two places.

## SDLC Branches

After design is complete, implementation planning and test planning are independent.

They are executed concurrently:

```text
               DESIGN
                  |
          +-------+-------+
          |               |
          v               v
   IMPLEMENTATION     TEST_PLAN
          |               |
          +-------+-------+
                  |
                  v
           SYNCHRONIZATION
                  |
                  v
               TESTING
```

The workflow records:

```text
PARALLEL_SDLC_BRANCH
SYNCHRONIZATION_POINT
PARALLEL_BRANCH_SYNCHRONIZED
```

Testing cannot begin until both branches have completed.

## Validation Branches

URL-format validation and policy validation are also independent and run concurrently using `CompletableFuture`.

```text
           Validation Required
                  |
          +-------+-------+
          |               |
          v               v
    URL Validation   Policy Validation
          |               |
          +-------+-------+
                  |
                  v
            Synchronize
```

This demonstrates non-linear execution and explicit synchronization.

---

# Gates and Controlled Execution

The workflow contains multiple control points.

## Entry Gate

Records admission into the orchestration workflow.

## Requirement Gate

Prevents downstream work when the requirement is ambiguous.

## Dependency Gates

Prevent SDLC stages from executing before their required predecessors have completed.

## Validation Gate

Prevents execution when required URL input is missing or invalid.

## Policy Gate

Prevents unsupported or unsafe URL schemes from reaching the side-effecting operation.

## Approval Gate

Pauses selected workflows until human approval is supplied.

## Release Readiness Gate

Verifies that required SDLC stages completed before the workflow is considered release-ready.

## Exit Gate

Marks successful completion only after execution, verification, documentation, and release-readiness checks have passed.

---

# Policy Guardrails

Before URL creation, the workflow performs policy checks.

Unsupported schemes include:

```text
file:
javascript:
data:
```

Policy enforcement occurs before the side-effecting persistence operation.

The policy implementation is intentionally small for this assessment, but the same boundary could be extended to security, compliance, environment, deployment, or organizational policies.

---

# Human Approval

Long-lived URLs demonstrate a human governance checkpoint.

When:

```text
expirationMinutes > 60
```

and approval has not been supplied, the workflow transitions to:

```text
WAITING_FOR_APPROVAL
```

Example:

```json
{
  "goal": "Create a long-lived short URL",
  "url": "https://www.google.com",
  "expirationMinutes": 120
}
```

No URL is persisted at this point.

For assessment purposes, approval can be demonstrated with:

```json
{
  "goal": "Create a long-lived short URL",
  "url": "https://www.google.com",
  "expirationMinutes": 120,
  "approved": true
}
```

A production implementation would use a dedicated approval endpoint and resume the original durable workflow rather than representing approval as a field on a new execution request.

---

# Stateful Sessions

Every orchestration execution receives a unique `sessionId`.

The session stores:

```text
Goal
Scenario
Requirement analysis
Workflow type
Status
Approval state
Reliability metrics
Tasks
Execution steps
Stage artifacts
Audit entries
```

Sessions are currently stored in a thread-safe `ConcurrentHashMap`.

The session can be retrieved through the orchestration session endpoint while the application is running.

For production use, this state should be persisted in a durable workflow/state store so that application restarts cannot lose an in-progress workflow.

---

# Dynamic Replanning

The workflow can react to certain safe, understood failures.

For example:

```text
google.com
```

does not initially satisfy the HTTP/HTTPS URL format expected by the validator.

The workflow can safely replan it as:

```text
https://google.com
```

Conceptually:

```text
Initial URL
    |
    v
Validation
    |
    v
Invalid Format
    |
    v
Replanning Decision
    |
    v
Add HTTPS when safe
    |
    v
Revalidate URL + Policy
    |
    +---------+
    |         |
  valid     invalid
    |         |
    v         v
continue   safe-stop
```

The corrected URL becomes the effective downstream value used for URL creation.

The workflow records the change using audit events such as:

```text
UPSTREAM_OUTPUT_CHANGED
DOWNSTREAM_REPLAN
```

This is bounded replanning.

The orchestration can choose a predefined safe correction. It cannot arbitrarily rewrite requirements or execute unrestricted actions.

---

# Retry and Recovery

URL creation uses a bounded retry policy.

```text
Maximum attempts = 3
```

Example:

```text
Attempt 1 -> Failure
      |
      v
Retry
      |
      v
Attempt 2 -> Success
      |
      v
Continue Workflow
```

The response records:

```text
attemptsUsed
retriesUsed
fallbackActivated
rollbackTriggered
replanned
executionTimeMs
```

Retries are intentionally bounded to prevent infinite execution loops.

---

# Fallback

If all URL-creation attempts fail, the primary execution path is considered exhausted.

Fallback does not silently change persistence mechanisms or pretend the request succeeded.

Instead, the workflow:

1. Records exhaustion of the primary path
2. Prevents uncontrolled continuation
3. Activates fallback
4. Enters the compensation/rollback path
5. Preserves failure information in the audit trail
6. Safely terminates execution

This keeps failure semantics explicit.

---

# Rollback

Rollback in this project is an orchestration-level compensation mechanism.

If URL creation fails after all retry attempts, the workflow:

```text
Stops downstream progression
        |
        v
Records fallback
        |
        v
Records rollback
        |
        v
Enters safe-stop state
```

It does not claim to reverse an already committed distributed transaction.

For a production workflow containing multiple successful side effects, explicit compensating operations or transactional boundaries would be needed.

---

# Safe Stop

The workflow deliberately stops when continuing automatically would be unsafe or misleading.

Examples include:

```text
Ambiguous requirement
Missing required URL
Policy violation
Retry exhaustion
Unrecoverable validation failure
```

These situations produce explicit waiting or failed states rather than allowing execution to drift forward.

---

# Audit Trail and Decision Lineage

Meaningful orchestration events are represented by `AuditEntry`.

Examples include:

```text
SESSION_CREATED
ENTRY_GATE
REQUIREMENT_ANALYZED
SDLC_DEPENDENCY_GRAPH_CREATED
STAGE_COMPLETED
PARALLEL_BRANCH_SYNCHRONIZED
APPROVAL_REQUIRED
EXECUTION_ATTEMPT
REPLAN_DECISION
UPSTREAM_OUTPUT_CHANGED
DOWNSTREAM_REPLAN
FALLBACK_ACTIVATED
ROLLBACK
SAFE_STOP
RELEASE_READINESS
EXIT_GATE
WORKFLOW_COMPLETED
```

Each audit entry records information such as:

```text
Timestamp
Action
Status
Details
```

This provides decision lineage for both successful and failed workflows.

---

# Reliability Metrics

Every orchestration response includes execution-level reliability information.

```text
attemptsUsed
retriesUsed
fallbackActivated
rollbackTriggered
replanned
executionTimeMs
```

Example:

```json
{
  "attemptsUsed": 2,
  "retriesUsed": 1,
  "fallbackActivated": false,
  "rollbackTriggered": false,
  "replanned": false,
  "executionTimeMs": 18
}
```

These metrics are currently per execution.

A production system could aggregate them across workflows to track:

```text
Success rate
Retry rate
Fallback rate
Rollback rate
Mean recovery time
p50 / p95 / p99 execution latency
```

Spring Boot Actuator is also enabled for application-level health and metrics.

---

# High-Level Architecture

```text
                         Client / Postman
                               |
                +--------------+--------------+
                |                             |
                v                             v
       URL Shortener API              Orchestration API
                |                             |
                v                             v
      UrlShortenerService             Requirement Analysis
                |                             |
                v                             v
       ShortUrlRepository                  DESIGN
                |                             |
                v                     +-------+-------+
           PostgreSQL                 |               |
                                      v               v
                               IMPLEMENTATION     TEST_PLAN
                                      |               |
                                      +-------+-------+
                                              |
                                              v
                                       Synchronization
                                              |
                                              v
                                      Validation / Policy
                                              |
                                              v
                                      Governance Gates
                                              |
                                              v
                                        URL Execution
                                              |
                                     +--------+--------+
                                     |                 |
                                     v                 v
                                  Success            Failure
                                     |                 |
                                     v                 v
                                  TESTING        Retry / Replan
                                     |                 |
                                     v                 v
                              DOCUMENTATION     Fallback / Rollback
                                     |                 |
                                     v                 v
                            RELEASE_READINESS      Safe Stop
                                     |
                                     v
                                  Exit Gate
```

---

# Project Structure

```text
src/main/java/com/neha/urlshortener
|
├── controller
│   ├── RedirectController.java
│   └── UrlShortenerController.java
|
├── domain
│   └── ShortUrl.java
|
├── dto
│   ├── AnalyticsResponse.java
│   ├── ShortenUrlRequest.java
│   └── ShortenUrlResponse.java
|
├── repository
│   └── ShortUrlRepository.java
|
├── service
│   └── UrlShortenerService.java
|
└── orchestration
    |
    ├── controller
    │   └── AgentController.java
    |
    ├── model
    │   ├── AgentRequest.java
    │   ├── AgentResponse.java
    │   ├── AgentStep.java
    │   ├── AgentTask.java
    │   ├── AuditEntry.java
    │   ├── DependencyGraph.java
    │   ├── Gate.java
    │   ├── OrchestrationSession.java
    │   ├── ParallelValidationResult.java
    │   ├── ReliabilityMetrics.java
    │   ├── ReplanningDecision.java
    │   ├── RequirementAnalysis.java
    │   ├── RetryPolicy.java
    │   ├── ScenarioType.java
    │   ├── SdlcStage.java
    │   ├── StageArtifact.java
    │   └── WorkflowType.java
    |
    └── service
        └── AgentOrchestrationService.java
```

---

# Running the Project

## Prerequisites

```text
Java 17+
Docker
Docker Compose
```

Start the supporting services:

```bash
docker compose up -d
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS/Linux:

```bash
./mvnw spring-boot:run
```

The application starts at:

```text
http://localhost:8080
```

---

# Configuration

Local configuration supports environment-variable overrides.

Example:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/urlshortener}
    username: ${DB_USERNAME:urluser}
    password: ${DB_PASSWORD:urlpassword}
```

The defaults support the local Docker environment while allowing external configuration for other environments.

---

# Testing

The project currently contains:

```text
14 orchestration behavior tests
1 application-level placeholder/smoke test
15 tests total
```

The orchestration tests cover:

| # | Scenario |
|---:|---|
| 1 | Standard URL workflow |
| 2 | Expiring URL workflow |
| 3 | Human approval checkpoint |
| 4 | Approved long-lived workflow |
| 5 | Missing URL failure |
| 6 | Stateful session storage |
| 7 | Brownfield scenario |
| 8 | Ambiguous requirement safe-stop |
| 9 | Temporary failure and retry |
| 10 | Retry exhaustion, fallback, rollback, and safe-stop |
| 11 | Dynamic URL replanning and downstream propagation |
| 12 | Reliability metric generation |
| 13 | Complete seven-stage SDLC lifecycle |
| 14 | Parallel SDLC execution and synchronization |

Run the complete test suite on Windows:

```powershell
.\mvnw.cmd clean test
```

On macOS/Linux:

```bash
./mvnw clean test
```

Current validated result:

```text
Tests run: 15
Failures: 0
Errors: 0
BUILD SUCCESS
```

The URL-shortener APIs can also be exercised through Postman for URL creation, redirects, analytics, expiration, and invalid/missing URL cases.

---

# Engineering Decisions

## Why PostgreSQL?

Shortened URL mappings are durable application state.

PostgreSQL provides transactional persistence and a straightforward path for indexing and extending the data model.

## Why `SecureRandom`?

Short codes should not follow an easily predictable sequence.

The URL service uses `SecureRandom` over an alphanumeric character set and checks for an existing short code before persistence.

## Why an explicit dependency graph?

A workflow is easier to understand when dependencies are represented directly rather than implied by source-code order.

`DependencyGraph` provides readiness checks that the orchestration uses to decide whether a stage can proceed.

This also provides a foundation for a more general scheduler that could execute all currently ready tasks independently.

## Why stage artifacts?

A workflow should expose intermediate engineering outcomes rather than only a final success/failure result.

`StageArtifact` makes requirements, design, implementation planning, testing, documentation, and release-readiness outputs reviewable.

## Why deterministic orchestration?

The execution layer should remain predictable and auditable.

An LLM could be useful at the planning boundary for interpreting natural-language requirements or proposing a task graph. However, persistence, policies, approvals, retries, and rollback should remain behind explicit engineering controls.

This separation makes the system easier to test, reason about, and audit.

## Why bounded autonomy?

Not every uncertain situation should be solved automatically.

The workflow deliberately stops or waits when:

- Requirements are ambiguous
- Required input is missing
- Policy validation fails
- Human approval is required
- Retries are exhausted

The goal is safe and useful automation rather than maximum autonomy.

---

# Current Limitations

This is an assessment implementation, so several components are intentionally lightweight.

## Deterministic Runtime

The runtime does not currently call an external LLM API.

AI-assisted development was used during implementation, while runtime orchestration remains deterministic and testable.

A future version could introduce an LLM at the planning boundary while retaining deterministic execution controls.

## In-Memory Workflow State

`ConcurrentHashMap` stores orchestration sessions.

Application restarts therefore lose session state.

A production implementation should use durable workflow/state storage.

## Simplified Approval Flow

Approval is represented using the `approved` request field.

A production implementation should expose dedicated approval/resume APIs tied to the original durable workflow session.

## Orchestration-Level Rollback

Rollback represents workflow compensation after execution failure.

It is not a distributed transaction or database rollback mechanism.

## Bounded Replanning

Replanning currently supports predefined safe corrections rather than arbitrary autonomous modification.

## Lightweight Policy Engine

Policies demonstrate where enforcement belongs, but they are currently embedded in the orchestration implementation.

A larger system would likely externalize policy configuration.

## Retry Strategy

Retries are bounded, but the current implementation does not include sophisticated error classification or exponential backoff.

## Per-Execution Reliability Metrics

Reliability metrics are captured per workflow execution but are not aggregated into historical SLO dashboards.

## In-Memory Session Growth

Sessions are not currently expired or evicted automatically.

A production implementation should define retention and cleanup policies.

## Persistence and Scaling Improvements

Additional production work could include schema migrations, stronger short-code uniqueness guarantees under concurrency, and atomic click-count updates.

---

# If I Took This Further

The next production-oriented improvements would include:

- Durable orchestration state
- Dedicated approval and workflow-resume APIs
- Persistent audit history
- Idempotency keys
- Authentication and authorization
- Rate limiting
- Structured error classification
- Exponential retry backoff
- Durable workflow workers
- Compensating transactions for multi-resource workflows
- OpenTelemetry tracing
- Prometheus/Grafana dashboards
- Aggregate reliability and SLO reporting
- Externalized policy rules
- Dead-letter handling
- Schema migrations
- Atomic analytics updates
- Stronger database-level short-code uniqueness handling
- Redis caching where it provides measurable value

For a more agentic runtime, I would add an LLM at the planning boundary to interpret natural-language requirements and propose task graphs.

The generated plan would still be validated and executed through the existing dependency checks, gates, policies, approval boundaries, retry limits, audit trail, and safe-stop controls.

---

# Assessment Coverage at a Glance

| Area | Implementation |
|---|---|
| Functional URL shortener | Spring Boot REST service + PostgreSQL |
| Requirement understanding | `RequirementAnalysis` + requirements artifact |
| Greenfield scenario | Explicit greenfield path |
| Brownfield scenario | Existing-system impact analysis |
| Ambiguous scenario | Clarification boundary / safe-stop |
| Full SDLC lifecycle | Seven explicit `SdlcStage` values |
| Reviewable outputs | `StageArtifact` |
| Task decomposition | `AgentTask` |
| Dependencies | Executable `DependencyGraph` readiness checks |
| Non-linear workflow | Conditional and parallel paths |
| Parallel SDLC work | Implementation + test-plan branches |
| Parallel validation | URL + policy validation |
| Synchronization | Explicit joins before dependent work |
| Gates | Entry, requirement, dependency, validation, policy, approval, release and exit controls |
| Human approval | Long-lived URL approval checkpoint |
| Stateful execution | Session ID + in-memory session state |
| Retry | Bounded 3-attempt policy |
| Dynamic replanning | Safe HTTPS normalization with downstream propagation |
| Fallback | Controlled fallback after retry exhaustion |
| Rollback | Orchestration-level compensation |
| Safe stop | Explicit waiting/failed paths |
| Policy guardrails | URL scheme policy validation |
| Auditability | Timestamped decision/audit history |
| Reliability | Attempts, retries, fallback, rollback, replan, execution time |
| Automated testing | 14 orchestration tests + 1 application test |

---

# AI-Assisted Development

AI-assisted development tools were used during the implementation process for brainstorming, debugging support, code review, test refinement, and documentation.

The runtime itself does not depend on an external LLM API.

The final implementation uses deterministic orchestration for execution so that dependencies, gates, policies, retries, approvals, replanning decisions, and failure handling remain explicit and testable.

---

# Final Notes

The URL shortener is intentionally straightforward.

The main engineering focus of this project is the orchestration around it: moving a requirement through explicit SDLC stages while deciding when automation can safely continue and when human input or a controlled stop is required.

The workflow can understand and classify a request, generate reviewable stage outputs, enforce dependencies, execute independent work concurrently, synchronize branches, apply governance gates, retry bounded failures, replan within predefined limits, preserve decision lineage, and expose reliability information.

At the same time, ambiguity, policy violations, approval requirements, and exhausted retries remain explicit boundaries.

That balance between automation and engineering control is the central design principle of the project.