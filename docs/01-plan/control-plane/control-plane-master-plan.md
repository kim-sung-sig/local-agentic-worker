# Control Plane Master Plan

> **For agentic workers:** Execute one child plan at a time. Each child plan is one responsibility and must end in one commit.

**Goal:** Make a remote Git Project and a directly registered Issue produce one durable, versioned `WorkRequested` integration message without coupling Control Plane to Temporal or an AI provider.

**Architecture:** `project` and `issue` remain the Control Plane core. Their ports are defined in the application layer and implemented by JPA/Kafka adapters. `contracts` is the only code shared with Agent Engine; the legacy local-path `agent` route is not used by new registrations.

**Tech Stack:** Java 21, Spring Boot 3.5, Gradle, PostgreSQL/Flyway, Spring Data JPA, Kafka, JUnit 5, Mockito.

---

## Scope

```mermaid
sequenceDiagram
    participant User
    participant CP as Control Plane
    participant DB as PostgreSQL
    participant Outbox as Work-request outbox
    participant Broker as Message broker

    User->>CP: Register remote Project
    CP->>DB: Save repository URI and base branch
    User->>CP: Create Issue
    CP->>DB: Save Issue
    CP->>Outbox: Save WorkRequested record in same transaction
    Outbox->>Broker: Publish WorkRequested asynchronously
```

### In scope

- Remote Git repository URI, base branch, optional credential reference.
- Direct Project and Issue registration.
- Transactional creation and asynchronous delivery of a versioned `WorkRequested`.
- Control Plane package/module boundary preparation and focused verification.

### Out of scope

- Starting Temporal workflows from Control Plane.
- AI model selection, CLI/API execution, worktree creation, QA execution, PR creation.
- External Issue ingestion (GitHub/Jira/Notion/Slack) and authentication UI.
- Repairing legacy `agent` tests unrelated to the new Control Plane path.

## Child-plan order

| Order | Plan | Single responsibility | Commit message |
|---:|---|---|---|
| CP-01 | [Remote Project domain model](CP-01-remote-project-domain.plan.md) | Express remote repository registration in the domain model | `feat: model remote git project` |
| CP-02 | [Remote Project persistence](CP-02-remote-project-persistence.plan.md) | Persist and retrieve remote Project fields safely | `feat: persist remote git project` |
| CP-03 | [Project registration API](CP-03-project-registration-api.plan.md) | Expose remote Project registration through the Control Plane API | `feat: register remote git project` |
| CP-04 | [Issue work request](CP-04-issue-work-request.plan.md) | Persist and publish one work request after Issue creation | `feat: publish issue work request` |
| CP-05 | [Control Plane verification](CP-05-control-plane-verification.plan.md) | Establish an executable happy-path verification gate | `test: verify control plane work request flow` |

## Cross-plan invariants

- A repository URI is remote (`https`, `http`, or `ssh`) and never a filesystem URI.
- `credentialRef` identifies a secret managed elsewhere; it never contains a token/password and is never emitted in `WorkRequested`.
- New Project creation does not require a local checkout.
- One Issue ID maps to one deterministic `WorkRequested.workflowId()`.
- A failed broker publish cannot erase the saved Issue or silently lose its pending work request.
- No Control Plane class imports Temporal SDK types or Agent Runtime classes.

## Exit criteria

1. A valid remote Project is stored and retrievable without `localPath`.
2. An Issue against that Project stores exactly one pending work request in the same transaction.
3. A dispatcher publishes the request and records delivery success or retryable failure.
4. Focused Control Plane tests pass without requiring the legacy Agent tests or a live PostgreSQL process.
5. The task commits exist in CP-01 through CP-05 order.
