# Agentic SDLC URL Shortener

A Java/Spring Boot URL shortener with a lightweight SDLC orchestration layer built around one idea:

**automation should be useful, but it should also be explainable and controlled.**

The application itself provides URL shortening, redirects, expiration, click analytics, and PostgreSQL persistence. On top of that, I built an orchestration layer that takes an engineering goal, understands the type of work being requested, breaks it into dependent tasks, applies validation and approval gates, executes independent work in parallel where appropriate, and keeps an audit trail of what happened.

Rather than trying to make the system look artificially autonomous, I kept the orchestration deterministic and testable. The agent can make bounded decisions, but ambiguous or unsafe situations deliberately stop instead of being guessed through.

---

## What I Built

There are two main parts to the project:

### 1. URL Shortener

A working Spring Boot backend that can:

- Create short URLs
- Generate unique 7-character short codes
- Redirect short URLs to their original destinations
- Track click counts
- Return URL analytics
- Create URLs with optional expiration
- Reject expired URLs
- Persist mappings in PostgreSQL

### 2. SDLC Orchestration Layer

An orchestration API that demonstrates:

- Requirement interpretation
- Greenfield, brownfield, and ambiguous scenarios
- Task decomposition
- Explicit task dependencies
- Conditional execution paths
- Parallel validation and synchronization
- Entry, validation, policy, approval, and exit gates
- Human approval checkpoints
- Stateful workflow sessions
- Bounded retries
- Dynamic replanning
- Fallback behavior
- Orchestration-level rollback
- Safe-stop behavior
- Decision/audit history
- Reliability metrics

---

## Tech Stack

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

# URL Shortener

## Create a Short URL

```http
POST /api/urls
```

Example request:

```json
{
  "originalUrl": "https://www.google.com",
  "expirationMinutes": 30
}
```

`expirationMinutes` is optional.

The service generates a random 7-character alphanumeric short code and checks for a collision before saving it.

---

## Redirect

```http
GET /{shortCode}
```

A valid short code redirects the caller to the original URL.

The redirect also increments the URL's click count.

Expired URLs are rejected rather than redirected.

---

## Analytics

```http
GET /api/urls/{shortCode}/analytics
```

This returns information about the shortened URL, including its click count.

Analytics retrieval itself does not increment the click count.

---

# How the Orchestration Works

The orchestration endpoint is:

```http
POST /api/agent/execute
```

A request contains an engineering goal plus the information needed to execute it.

For example:

```json
{
  "goal": "Create a new URL shortening capability",
  "url": "https://www.google.com",
  "scenarioType": "GREENFIELD"
}
```

The orchestration response includes more than the final result. It also explains how the result was reached:

- Session ID
- Scenario classification
- Normalized requirement
- Selected workflow
- Current status
- Approval requirement
- Generated short URL
- Task list
- Task dependencies
- Execution steps
- Audit history
- Reliability metrics

That makes an execution inspectable rather than treating the agent as a black box.

---

# Requirement Understanding

Before doing the actual work, the request is converted into a `RequirementAnalysis`.

It captures:

- Original engineering goal
- Normalized requirement
- Scenario type
- Whether the request is ambiguous
- Assumptions being made
- Existing components that may be affected

For example, a request to enhance the existing URL shortener is treated differently from a request to build a new capability.

---

# Scenario Handling

I modeled three scenarios because they exercise different types of engineering reasoning.

## Greenfield

Example:

```json
{
  "goal": "Create a new URL shortening capability",
  "url": "https://www.google.com",
  "scenarioType": "GREENFIELD"
}
```

The workflow treats this as a new capability and creates the corresponding implementation path.

Typical flow:

```text
Understand Requirement
        |
        v
Greenfield Planning
        |
        v
Select Workflow
        |
        v
Validation / Policy Checks
        |
        v
Execute
        |
        v
Verify
```

---

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

Here the system reasons about modifying an existing application rather than treating the request as a clean-slate implementation.

The analysis identifies impacted components such as:

```text
UrlShortenerController
UrlShortenerService
ShortUrlRepository
AgentOrchestrationService
```

The assumptions also capture concerns such as maintaining existing API behavior and not breaking previously persisted URLs.

This is intentionally modeled as deterministic impact analysis for the assessment; it is not pretending to perform autonomous source-code understanding.

---

## Ambiguous Requirement

Example:

```json
{
  "goal": "Maybe change something in the URL system",
  "url": "https://www.google.com",
  "scenarioType": "AMBIGUOUS"
}
```

In this case, executing immediately would mean guessing.

Instead, the workflow moves to:

```text
WAITING_FOR_CLARIFICATION
```

and records why it stopped.

I preferred this behavior because a useful engineering agent should know when **not** to act.

---

# Task Decomposition and Dependencies

The orchestration layer does not represent the request as one large operation.

It breaks work into `AgentTask` objects, and each task records the IDs of the tasks it depends on.

A simplified graph looks like this:

```text
                  TASK-1
          Understand Requirement
                     |
                     v
                  TASK-2
       Greenfield / Brownfield Plan
                     |
                     v
                  TASK-3
             Select Workflow
                     |
             +-------+-------+
             |               |
             v               v
          TASK-4A         TASK-4B
        URL Format         Policy
        Validation        Validation
             |               |
             +-------+-------+
                     |
                     v
              Synchronization
                     |
                     v
          Approval / Expiration
                     |
                     v
              Create Short URL
                     |
                     v
                Verification
                     |
                     v
                  Exit Gate
```

`DependencyGraph` keeps an explicit representation of those relationships rather than relying only on the order of Java statements.

---

# Parallel Work and Synchronization

Some tasks do not need to wait for each other.

URL-format validation and policy validation are independent, so they are executed concurrently using `CompletableFuture`.

```text
                 Workflow Selected
                        |
              +---------+---------+
              |                   |
              v                   v
       Format Validation     Policy Validation
              |                   |
              +---------+---------+
                        |
                        v
                Synchronization
```

The workflow waits at a synchronization point until both checks have completed.

Only then can execution continue.

This gives the orchestration a real non-linear execution path rather than representing every task as a sequential list.

---

# Gates and Controlled Execution

I use gates to prevent the workflow from continuing simply because a previous method returned.

### Entry Gate

Records admission into the orchestration workflow.

### Validation Gate

Prevents execution when required input is missing or cannot be safely interpreted.

### Policy Gate

Blocks unsupported or unsafe URL schemes.

### Approval Gate

Pauses selected workflows until human approval is supplied.

### Exit Gate

Marks successful completion only after the required execution and verification stages have passed.

---

# Policy Guardrails

Before a URL is created, the orchestration performs policy checks.

For example, unsupported schemes such as these are not allowed:

```text
file:
javascript:
data:
```

The current policy set is intentionally small. The important design decision is that policy enforcement happens **before** the side-effecting operation.

In a larger system, this layer could enforce security, compliance, environment, deployment, or organizational rules.

---

# Human Approval

I used long-lived URLs as a simple example of a workflow that should require additional governance.

When:

```text
expirationMinutes > 60
```

and approval has not been supplied, execution moves to:

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

No short URL is created at that point.

For the assessment, approval can be demonstrated by sending:

```json
{
  "goal": "Create a long-lived short URL",
  "url": "https://www.google.com",
  "expirationMinutes": 120,
  "approved": true
}
```

This keeps the example simple.

In production, I would use a dedicated approval endpoint and resume the original durable workflow session instead of submitting approval through a new execution request.

---

# Stateful Sessions

Every orchestration request receives a unique `sessionId`.

The session records:

```text
Goal
Scenario
Requirement analysis
Workflow type
Status
Approval state
Reliability metrics
Tasks
Steps
Audit entries
```

Sessions are stored in a thread-safe `ConcurrentHashMap`.

This makes workflow state available while the application is running.

For production, this is one of the first things I would change: session state should live in a durable workflow or state store so an application restart cannot lose an in-progress execution.

---

# Retry and Recovery

Transient failures should not always fail the entire workflow immediately.

URL creation therefore uses a bounded retry policy:

```text
Maximum attempts = 3
```

A recoverable execution might look like:

```text
Attempt 1 -> Failed
Attempt 2 -> Successful
             |
             v
          Continue
```

The response records:

- Total attempts
- Number of retries
- Whether fallback was needed

Retries are intentionally bounded so a broken dependency cannot cause an infinite execution loop.

---

# Dynamic Replanning

A workflow can also react to information discovered during execution.

For example:

```text
google.com
```

does not initially satisfy the expected HTTP/HTTPS URL format.

Instead of immediately giving up, the orchestration records a replanning decision and evaluates whether the input can safely be interpreted using HTTPS.

Conceptually:

```text
Initial Validation
       |
       v
    Failed
       |
       v
Replanning Decision
       |
       v
Evaluate HTTPS Interpretation
       |
   +---+---+
   |       |
 valid   invalid
   |       |
   v       v
continue  stop
```

This is deliberately **bounded replanning**.

The orchestration can choose between known safe paths; it cannot arbitrarily rewrite requirements or execute unrestricted actions.

---

# Fallback

If all URL-creation attempts fail, the workflow activates a fallback policy.

In this project, fallback does **not** silently switch to another persistence mechanism or pretend the operation succeeded.

Instead it:

1. Records that the primary path was exhausted
2. Prevents uncontrolled continuation
3. Enters the compensation/rollback path
4. Preserves the failure in the audit history
5. Safely terminates execution

That choice is intentional. A fallback should not change the semantics of the requested operation just to produce a successful status.

---

# Rollback

Rollback here is an **orchestration-level compensation step**.

It is important to be precise about that.

If all URL-creation attempts fail, there is no successfully created short URL for the orchestration to return. The workflow therefore abandons further progression, records rollback, preserves its failed state, and safely stops.

It does **not** claim to delete an already committed database record.

For a production workflow involving multiple successful side effects, I would implement explicit compensating operations or transactional boundaries for each resource that needs to be reversed.

---

# Safe Stop

There are situations where continuing automatically would be worse than stopping.

Examples include:

```text
Ambiguous requirement
Missing required input
Policy violation
Retry exhaustion
```

These conditions produce an explicit stopped or failed workflow state instead of allowing execution to drift forward.

This is one of the main ways controlled autonomy is enforced in the project.

---

# Audit Trail and Decision Lineage

Each meaningful orchestration event becomes an `AuditEntry`.

Examples include:

```text
SESSION_CREATED
ENTRY_GATE
REQUIREMENT_ANALYZED
PARALLEL_BRANCH_SYNCHRONIZED
APPROVAL_REQUIRED
EXECUTION_ATTEMPT
REPLAN_DECISION
FALLBACK_ACTIVATED
ROLLBACK
SAFE_STOP
EXIT_GATE
WORKFLOW_COMPLETED
```

Each entry records:

```text
Timestamp
Action
Status
Details
```

Because of that, a completed or failed workflow can be inspected afterward to understand **what happened and why**.

---

# Reliability Metrics

Each orchestration response includes execution-level reliability information:

```text
attemptsUsed
retriesUsed
fallbackActivated
rollbackTriggered
replanned
executionTimeMs
```

For example:

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

These are per-execution metrics.

For a production system, I would aggregate them across workflow runs to track things such as:

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

# Example Successful Orchestration

Request:

```json
{
  "goal": "Create a new URL shortening capability",
  "url": "https://www.google.com",
  "scenarioType": "GREENFIELD"
}
```

A successful response contains information similar to:

```json
{
  "sessionId": "generated-session-id",
  "goal": "Create a new URL shortening capability",
  "scenarioType": "GREENFIELD",
  "workflowType": "STANDARD_URL",
  "status": "COMPLETED",
  "message": "URL shortening workflow completed successfully",
  "shortUrl": "http://localhost:8080/abc1234",
  "approvalRequired": false,
  "reliabilityMetrics": {
    "attemptsUsed": 1,
    "retriesUsed": 0,
    "fallbackActivated": false,
    "rollbackTriggered": false,
    "replanned": false,
    "executionTimeMs": 10
  }
}
```

The actual response additionally includes requirement analysis, tasks, execution steps, and audit history.

---

# High-Level Architecture

```text
                         Client / Postman
                               |
               +---------------+---------------+
               |                               |
               v                               v
       URL Shortener API              Orchestration API
               |                               |
               v                               v
     UrlShortenerService             Requirement Analysis
               |                               |
               v                               v
      ShortUrlRepository             Scenario Classification
               |                               |
               v                               v
          PostgreSQL                  Dependency Graph
                                               |
                                     +---------+---------+
                                     |                   |
                                     v                   v
                               URL Validation      Policy Validation
                                     |                   |
                                     +---------+---------+
                                               |
                                               v
                                       Synchronization
                                               |
                                               v
                                      Governance Gates
                                               |
                                               v
                                       URL Execution
                                               |
                                     +---------+---------+
                                     |                   |
                                     v                   v
                                  Success              Failure
                                     |                   |
                                     v                   v
                                  Verify          Retry / Replan
                                     |                   |
                                     v                   v
                                 Exit Gate       Fallback / Rollback
                                                         |
                                                         v
                                                     Safe Stop
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
    │   └── WorkflowType.java
    |
    └── service
        └── AgentOrchestrationService.java
```

---

# Running the Project

## Prerequisites

You will need:

```text
Java 17+
Docker
Docker Compose
```

Clone the repository and move into the project directory.

Start the supporting services:

```bash
docker compose up -d
```

On Windows, run the application with:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS/Linux:

```bash
./mvnw spring-boot:run
```

The application starts on:

```text
http://localhost:8080
```

---

# Configuration

Local configuration supports environment-variable overrides.

For example:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/urlshortener}
    username: ${DB_USERNAME:urluser}
    password: ${DB_PASSWORD:urlpassword}
```

The defaults make the Docker-based local environment easy to start, while external configuration can be supplied in other environments.

---

# Testing

The orchestration unit tests currently cover twelve behaviors:

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
| 11 | Dynamic replanning |
| 12 | Reliability metric generation |

Run all tests on Windows:

```powershell
.\mvnw.cmd test
```

On macOS/Linux:

```bash
./mvnw test
```

The URL-shortener APIs were also manually exercised using Postman for URL creation, redirect behavior, analytics, expiration, and invalid/missing URL cases.

---

# A Few Engineering Decisions

### Why PostgreSQL?

Shortened URL mappings are durable application state. PostgreSQL gives the service transactional persistence and a straightforward path for indexing and scaling the data model.

### Why `SecureRandom`?

Short codes should not follow an easily predictable sequence. The service uses `SecureRandom` over an alphanumeric character set and checks for an existing code before persistence.

### Why an explicit dependency graph?

A workflow is easier to reason about when dependencies are part of the model rather than being implied by method order.

It also gives a natural path toward scheduling ready tasks independently in a more advanced orchestration engine.

### Why deterministic orchestration instead of an LLM everywhere?

I wanted the execution layer to remain predictable.

An LLM could be useful for interpreting a natural-language requirement or proposing a plan, but actions such as persistence, policy enforcement, approvals, retries, and rollback should still operate inside explicit engineering controls.

This separation keeps the system easier to test, explain, and audit.

### Why bounded autonomy?

Not every uncertain situation should be solved automatically.

The workflow deliberately stops or waits when:

- the requirement is unclear
- a policy rule fails
- human approval is required
- retries are exhausted

The goal is not maximum automation. The goal is **safe and useful automation**.

---

# Current Limitations

This is an assessment implementation, so I intentionally kept several pieces lightweight.

### In-memory workflow state

`ConcurrentHashMap` is used for orchestration sessions.

A restart loses that state. A production implementation should persist sessions in a durable store.

### Simplified approval flow

Approval is represented by the `approved` request field.

A production version should expose approval/resume APIs tied to the original session.

### Orchestration-level rollback

Rollback currently represents workflow compensation after the creation operation has failed.

It is not presented as a distributed transaction or database compensation mechanism.

### Bounded replanning

Replanning uses predefined safe behavior rather than arbitrary autonomous modification.

### Lightweight policy engine

The current policies demonstrate where enforcement belongs. A real enterprise system would likely externalize policy configuration.

### Execution-level reliability metrics

Metrics are captured for each orchestration response but are not yet aggregated into historical SLO dashboards.

---

# If I Took This Further

The next production-oriented improvements I would make are:

- Durable orchestration state
- Dedicated approval and workflow-resume APIs
- Persistent audit history
- Idempotency keys
- Authentication and authorization
- Rate limiting
- Structured error classification
- Exponential retry backoff
- Distributed workers or a durable workflow engine
- Compensating transactions for multi-resource workflows
- OpenTelemetry tracing
- Prometheus/Grafana dashboards
- Aggregate reliability/SLO reporting
- Externalized policy rules
- Dead-letter handling
- Redis caching where it provides measurable value

For a more agentic version, I would add an LLM at the **planning boundary** to interpret natural-language requirements and propose task graphs, while keeping actual execution behind the existing deterministic gates, policies, approvals, and audit controls.

---

# Assessment Coverage at a Glance

| Area | Implementation |
|---|---|
| Functional URL shortener | Spring Boot REST service + PostgreSQL |
| Requirement understanding | `RequirementAnalysis` |
| Greenfield scenario | Explicit greenfield planning path |
| Brownfield scenario | Existing-system impact path |
| Ambiguous scenario | Clarification gate / safe stop |
| Task decomposition | `AgentTask` |
| Dependencies | `DependencyGraph` |
| Non-linear workflow | Conditional branches and scenario paths |
| Parallel execution | `CompletableFuture` validation branches |
| Synchronization | Explicit join before execution |
| Gates | Entry, validation, policy, approval, exit |
| Human approval | Long-lived URL approval checkpoint |
| Stateful execution | Session state + session ID |
| Retry | Bounded 3-attempt policy |
| Dynamic replanning | Safe URL-format replanning path |
| Fallback | Controlled fallback after retry exhaustion |
| Rollback | Orchestration-level compensation |
| Safe stop | Explicit stopped/failed execution paths |
| Policy guardrails | URL scheme policy validation |
| Auditability | Timestamped `AuditEntry` history |
| Reliability | Attempts, retries, fallback, rollback, replan, duration |
| Automated testing | 12 orchestration behavior tests |

---

# Final Notes

The URL shortener is intentionally straightforward; the more interesting part of this exercise for me was deciding **where automation should continue and where it should stop**.

The orchestration layer can classify work, create dependent execution paths, run independent checks concurrently, retry temporary failures, replan within defined boundaries, and expose the reasoning behind an execution.

At the same time, ambiguity, policy failures, approval requirements, and exhausted retries remain explicit boundaries.

That balance between automation and engineering control is the main design principle behind the project.

---

## AI-Assisted Development

AI-assisted development tools were used as part of the implementation workflow for brainstorming, debugging support, code review, and documentation refinement.

The final implementation, engineering decisions, test behavior, trade-offs, and project structure were reviewed and validated against the intended behavior of the application.