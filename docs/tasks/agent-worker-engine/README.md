# Agent Worker Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the durable six-stage Agent Worker Engine defined in [the approved specification](../../specs/agent-worker-engine.md).

**Architecture:** Add an `engine` bounded context for the Temporal workflow, persisted workflow projection, gates, and attempt history. The engine calls versioned Temporal Activities and never performs Git, filesystem, CLI, or model I/O itself. Existing `agent` code remains intact until each replacement path is proven.

**Tech Stack:** Java 21, Spring Boot 3.5, PostgreSQL/Flyway, Temporal Java SDK, JUnit 5, Mockito, Testcontainers.

---

The dependency order is mandatory. See [the task flow diagram](../../report/agent-worker-engine-task-flow.md).

| Order | Task | Completion evidence |
|---:|---|---|
| 1 | [T01 Temporal foundation](task-01-temporal-foundation.md) | Worker starts and a no-op workflow completes. |
| 2 | [T02 Engine state](task-02-engine-state.md) | Workflow run, stage gate, and attempt history persist. |
| 3 | [T03 Activity contracts](task-03-activity-contracts.md) | Versioned cross-language DTO contracts compile and serialize. |
| 4 | [T04 Six-stage workflow](task-04-six-stage-workflow.md) | Signals drive approved state transitions deterministically. |
| 5 | [T05 Workspace runtime](task-05-workspace-runtime.md) | One run acquires one reusable worktree. |
| 6 | [T06 Implementation and QA loop](task-06-implementation-qa-loop.md) | Attempt artifacts and QA reports drive the configured loop. |
| 7 | [T07 Source control gate](task-07-source-control-gate.md) | Draft PR and final merge require their respective gates. |
| 8 | [T08 API and integration QA](task-08-api-integration-qa.md) | APIs, observability, and end-to-end workflow checks pass. |

Each Task must finish with its own automated tests, static checks, and a review record before the next Task begins. Do not start a dependent Task from a partially completed predecessor.
