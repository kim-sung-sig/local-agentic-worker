# Agent Runtime Adapter — PLANNING Vertical Slice (Design)

**Status:** Approved design
**Date:** 2026-07-24
**Feature:** agent-runtime-planning-slice

## Summary

Replace the Python worker's *fake-work + PostgreSQL ledger* scaffold with the first real slice of the platform's **Agent Runtime adapter** (master plan Stage 5): a **stateless development-environment + agent-execution host** whose durable state is a **git worktree**, not a database.

For a `PLANNING` request the worker clones/prepares the project (idempotently), creates a per-run worktree/branch, runs a configurable agent command to generate a plan, commits it, and returns a **git reference**. **Temporal owns idempotency and durability**; the worker derives all state from git/filesystem. The Control Plane's automation of human approval gates is separate and out of scope here.

This supersedes, for the worker, the fake-execution ledger delivered in the Worker Gateway slice (`_workspace_gateway`). It does **not** change the master-plan boundary: Engine (Java/Temporal) owns workflow state/gates/retries; the worker is an Activity Worker.

## Decisions (from brainstorming)

1. **Scope** — the `PLANNING` stage only, as one end-to-end testable vertical slice.
2. **Agent runtime** — an abstract, configurable `AGENT_COMMAND` run as a subprocess. Concrete CLIs (Codex/Claude) are injected via configuration; tests use a fake command. No agent-vendor coupling in code.
3. **Execution model** — keep the existing `agent-worker/v1` async transport (`submit` → poll `status`/`events`) and the Gateway's sticky `workflowRunId → session` routing. Sticky routing is now justified by **workspace locality** (the worktree lives on one worker host).
4. **Plan artifact** — returned as a **git reference** `{branch, commitSha, planPath}`. The plan body is committed to the worktree branch; only the reference crosses the contract. IMPLEMENTATION (future) continues on the same branch.
5. **Workspace & idempotency** — two levels: one **base clone per project** (fetch if present) + one **worktree/branch per workflow run**. All idempotency is derived from git/filesystem state; **no database**.

## Architecture

Boundary is unchanged from the platform master plan:

```
Control Plane → contracts(agent-worker/v1) → Agent Engine (Temporal, Java)
      → Temporal PLANNING activity → Worker Gateway (sticky routing) → Python Agent Worker
```

The Python Agent Worker is decomposed into focused units:

- `workspace.py` — git operations: ensure base clone, ensure per-run worktree/branch, read commit state. One responsibility: manage on-disk git state under `WORKER_WORKSPACE_ROOT`.
- `planner.py` — run `AGENT_COMMAND` as a subprocess in a worktree with a timeout; produce the plan file; commit it. One responsibility: agent execution + commit.
- `jobs.py` — in-memory tracking of currently-running subprocesses and derivation of `status`/`events` from (in-memory running set) ∪ (git committed state). One responsibility: status/event projection.
- `app.py` — FastAPI HTTP handlers for the `agent-worker/v1` endpoints, delegating to the above.

## Worker behavior — PLANNING

Input (existing contract): `submit({ contractVersion:'agent-worker/v1', idempotencyKey, workflowRunId, stage:'PLANNING', attemptNumber, stageExecutionGeneration, adapterId, project:{ repositoryUri, baseBranch, credentialRef, requestedSourceCommit }, mode:'READ' })`.

1. **Ensure base clone.** Base path = `WORKER_WORKSPACE_ROOT/<repoKey>` where `repoKey` is a stable hash of `repositoryUri`. If missing → `git clone <repositoryUri> <basePath>` (credential resolved locally from `credentialRef`, never from the contract). If present → `git fetch`.
2. **Ensure worktree + branch.** Branch = `agent/plan/<workflowRunId>`; worktree path = `WORKER_WORKSPACE_ROOT/worktrees/<workflowRunId>`. If the worktree is missing → `git worktree add -b <branch> <worktreePath> <baseBranch-or-requestedSourceCommit>`. If present → reuse.
3. **Generate plan (idempotent).** If the plan commit already exists on `<branch>` (the plan file is present and committed) → skip the agent run and return the existing reference. Else run `AGENT_COMMAND` in the worktree (with `AGENT_TIMEOUT_SECONDS`), writing `docs/plans/<workflowRunId>.plan.md`, then `git add` + `git commit`.
4. **Return the reference.** Terminal status carries the plan reference `{branch, commitSha, planPath}`; ordered events `accepted → running → completed` are preserved (the reference travels in the completed event / status, not the plan body).

### State & idempotency (no DB)

- Durable state = git (base clone, branch, plan commit). The in-memory map in `jobs.py` tracks only in-flight subprocesses to report `RUNNING`.
- On worker restart mid-run, in-memory state is lost and status is re-derived from git: plan commit present → `COMPLETED`; absent → the execution is treated as re-runnable, and Temporal's retry re-submits, which re-runs the agent idempotently (existence checks in steps 1–3 make each step safe to repeat).
- Removed: `agent_worker.executions` / `execution_events` tables, `ledger.py`, `migrations/`, `WORKER_DATABASE_URL`, the `psycopg` dependency, and commit `1493d1e` (the ledger durability fix) is reverted.

## Contract & transport

- `agent-worker/v1` endpoints and error semantics are unchanged (`submit`, `status`, `events`, retryable `UNAVAILABLE`, sticky routing).
- The **status/event payload must carry the plan git reference**. Preferred: reuse the existing `artifactRefs: string[]` on `ExecutionStatus` to carry a serialized ref; if a structured field is clearer, add a small optional `planRef: { branch, commitSha, planPath }` — decided during planning to minimize contract churn while keeping downstream mapping clean.
- No `WorkspaceRef`, host paths, or secrets appear in any request or response. Only git refs and repo-relative paths cross the boundary.
- The Temporal PLANNING adapter maps the returned reference into `implementationPlanRef` (existing `EngineActivities.planImplementation` return type).

## Configuration

| Variable | Purpose | Test default |
|---|---|---|
| `WORKER_WORKSPACE_ROOT` | Root dir for base clones + worktrees | a temp dir |
| `AGENT_COMMAND` | Command run to generate the plan | a fake script writing a fixed plan file |
| `AGENT_TIMEOUT_SECONDS` | Subprocess timeout | small (e.g. 30) |
| credential resolution config | Maps `credentialRef` → local git credential/env | none needed for `file://`/local remote in tests |

## Testing (TDD, no real agent)

- Use a local **bare git repository** as the remote (`file://` or a local path) and a **fake `AGENT_COMMAND`** script that writes a fixed `docs/plans/<runId>.plan.md`.
- Behavioral assertions:
  1. `submit` PLANNING → poll `status` reaches `COMPLETED` with a valid git reference; the branch, commit, and plan file exist.
  2. Re-submitting the same run returns the **same commit** and does **not** re-run the agent (assert the fake command's side-effect/counter is unchanged).
  3. A second run for a different `workflowRunId` **reuses the base clone** (no second clone).
  4. `events` are ordered `accepted → running → completed`.
  5. No absolute host path and no secret value appears in any `submit`/`status`/`events` payload.
- Gateway and Temporal-adapter tests are largely unchanged (transport identical); only ledger-specific expectations are updated, and the PLANNING adapter test asserts the git ref maps into `implementationPlanRef`.

## Out of scope (deferred)

IMPLEMENTATION and QA stages; wiring a concrete Codex/Claude CLI; artifact store; Control-Plane approval-gate automation (gates remain Temporal signals); webhooks/SSE; PR creation; multi-host workspace migration.

## Files impacted (high level)

- `apps/python-agent-worker/src/agent_worker/`: add `workspace.py`, `planner.py`, `jobs.py`; rewrite `app.py`; remove `ledger.py`; trim `models.py` to the contract models still used; delete `migrations/`; update `pyproject.toml` (drop `psycopg`, keep `fastapi`/`uvicorn`).
- `apps/python-agent-worker/tests/`: replace ledger tests with the PLANNING-slice tests above.
- `docker-compose.dev.yml`: remove the `agent-worker-postgres` (ledger) service; keep the Temporal stack.
- `packages/contracts`: ensure the plan git reference is representable on the status/event (reuse `artifactRefs` or add optional `planRef`).
- Temporal PLANNING adapter + its tests: map the git ref into `implementationPlanRef`; update ledger-specific expectations.
- Revert commit `1493d1e`.

## Traceability

- Aligns with `docs/01-plan/platform-master-plan.md` Stage 5 ("First Agent Runtime adapter … repository assessment, implementation planning, implementation, and QA") and platform rules (contracts hold references not bodies/secrets; Java owns Temporal, Python may own Activity Workers).
- Supersedes the fake-ledger worker from the Worker Gateway slice (`_workspace_gateway/99_summary.md`) for the worker's responsibility.
