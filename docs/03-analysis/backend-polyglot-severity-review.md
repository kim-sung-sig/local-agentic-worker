# Backend Polyglot Severity Review

## Scope

- Branch: `codex/backend-worker-integration`
- Commits: `5b401a4`, `862f9b2`, `81d89c7`
- Review target: Java workflow ticket validation, Java-to-TypeScript Temporal handoff, and worker-mode configuration.
- Review method: source diff, focused tests, full Gradle test, and an isolated Java API smoke run using the TypeScript Temporal queue.

## Severity policy

| Level | Meaning | Release rule |
|---|---|---|
| Critical | Data loss, security breach, or system-wide outage | Must fix before any release |
| High | Core requested flow is unusable or a serious regression is introduced | Must fix before push/merge |
| Medium | Operational risk, incomplete integration, or a non-blocking defect | Mitigation or explicit release-owner acceptance required |
| Low | Maintenance, cleanup, or documentation debt | Track for follow-up |

## Summary

| Severity | Open | Resolved during review |
|---|---:|---:|
| Critical | 0 | 0 |
| High | 0 | 1 |
| Medium | 2 | 1 |
| Low | 1 | 0 |

## Findings

### HIGH-RESOLVED-001 — Java workflow type did not match TypeScript workflow type

- File: `src/main/java/com/example/worker/engine/workflow/AgentWorkerWorkflow.java:13`
- Symptom: Java started workflow type `AgentWorkerWorkflow`, while the TypeScript worker registers `run`.
- Evidence: the first isolated smoke query failed with `WorkflowQueryRejectedException`; the corrected run reached `PLANNING/RUNNING`.
- Resolution: `@WorkflowMethod(name = "run")` plus a regression assertion in `TemporalConfigurationTest`.
- Status: Resolved in `81d89c7`; spec and quality re-review approved.

### MEDIUM-001 — Existing in-flight Java workflows need migration handling

- File: `src/main/java/com/example/worker/engine/workflow/AgentWorkerWorkflow.java:13`
- Risk: deployments that change the workflow type can strand runs created under the previous Java type if the old worker is removed immediately.
- Mitigation: drain, cancel, or restart pre-change Java runs before enabling the polyglot queue. This is an operational rollout requirement, not a code blocker for a clean local environment.
- Status: Open operational follow-up.

### MEDIUM-002 — Workspace stage remains deferred in the existing TypeScript harness

- File: `apps/temporal-worker/src/activities/local-engine-activities.ts:20`
- Evidence: the Java polyglot smoke reached `PLANNING/RUNNING`; after planning approval it reached `WORKSPACE`, where the existing local activity intentionally throws `WORKSPACE stage is deferred in the local integration harness`.
- Impact: this branch proves the Java → TypeScript Temporal → Gateway/Python planning path, not a complete Workspace → Implementation → QA run.
- Status: Known scope boundary; requires a separate workspace-runtime task for full pipeline completion.

### MEDIUM-RESOLVED-001 — Polyglot smoke result was not persisted as a review artifact

- Evidence gap: the initial smoke result existed only in the orchestration session, so a reviewer could not reproduce the release decision from the repository alone.
- Resolution: this document now records the exact workflow ID, configuration, observed stage, and deferred-stage boundary.
- Status: Resolved by `8d7b847`.

### LOW-001 — Deprecated test annotation

- File: `src/test/java/com/example/worker/engine/api/controller/WorkflowRunControllerTest.java:47`
- Finding: Spring's `@MockBean` is deprecated and marked for removal.
- Impact: no current behavior risk; migrate when the project upgrades to the replacement test support.
- Status: Follow-up maintenance.

## Verification evidence

- `./gradlew test`: `BUILD SUCCESSFUL`.
- `TemporalConfigurationTest`: 4 tests, 0 failures, 0 errors.
- Java polyglot smoke: workflow `bf307eae-1593-4618-bc13-fc94da89a513` reached `PLANNING/RUNNING` after `APPROVE` through the Java API configured with `agent-worker-engine-typescript` and `worker-enabled=false`.
- Worktree after verification: clean.

## Release decision

No Critical or High findings remain. Push is technically permitted for the scoped change, subject to accepting the two Medium operational/scope items above. The full autonomous pipeline should not be advertised as complete until Workspace execution is implemented and smoke-tested.
