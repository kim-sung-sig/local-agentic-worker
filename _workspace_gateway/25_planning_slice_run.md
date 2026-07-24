# Agent Runtime PLANNING slice — local integration run (2026-07-24)

Full stack: Docker Temporal (7233) + Gateway (3001) + Python Worker (8000) + local `git daemon` remote (`git://127.0.0.1/repository.git`). Worker config: `WORKER_WORKSPACE_ROOT=C:\tmp\agent-workspace`, `AGENT_COMMAND=python C:/tmp/fake_agent.py` (fake agent writes the plan file). No database involved.

## Driver

`npm run integration:run -- planning-itest-002` →
`{"runId":"planning-itest-002","currentStage":"PLANNING","status":"RUNNING"}` (parks at the PLANNING gate).

## Ledger-free results (from the worker HTTP API)

- INTAKE `planning-itest-002:INTAKE:1:1` → `COMPLETED`, `artifactRefs=["agent/plan/planning-itest-002@b81942a142f54c3517540cd08e0b474747bef89f:docs/plans/planning-itest-002.plan.md"]`.
- PLANNING `planning-itest-002:PLANNING:1:3` → `COMPLETED`, **same** ref `...@b81942a...` (generation `3` = Temporal activityId). Events ordered `accepted → running → completed`.

## On-disk git state (durable artifact)

- Worktree: `C:\tmp\agent-workspace\worktrees\planning-itest-002`, branch `agent/plan/planning-itest-002`.
- `git log --oneline -1` → `b81942a plan: planning-itest-002`.
- `git rev-list --count HEAD` → `2` (seed + plan) — PLANNING did **not** create a second commit → agent not re-run → git-level idempotency confirmed across stages.
- Single base clone `C:\tmp\agent-workspace\base\be6c66ed952960ce` (one per project).

## No-leak sanitizer validated live

An earlier misconfigured attempt (worker pointed at the placeholder `https://.../acme/...`) failed at clone; the returned failed-event error was `Cloning into '[REDACTED]'... fatal: repository 'http[REDACTED]' not found` — the absolute path and URL were redacted by the `jobs.py` sanitizer (commit `0e0bbd9`).

## Notes / gotchas observed

- Stopping a background worker's shell can orphan its `node`/`tsx` child; two workers on the same task queue caused a failed run until the orphan tree was killed. Operational only — not a code defect.
- `PROJECT_REPOSITORY_URI`/`PROJECT_BASE_BRANCH` env vars on the Temporal worker select the remote (added to `apps/temporal-worker/src/main.ts`).
