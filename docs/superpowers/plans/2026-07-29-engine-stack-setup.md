# Engine Stack Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide one PowerShell command that prepares the complete local Engine test stack and leaves its endpoints ready for a later API or workflow smoke test.

**Architecture:** `scripts/start-engine-stack.ps1` creates disposable fixture state under the operating-system temporary directory. It starts the existing Temporal Docker profile plus a local HTTP Git fixture, Python Worker, Worker Gateway, and Temporal Worker as child processes, then validates the available readiness contracts.

**Tech Stack:** Windows PowerShell 7+, Docker Compose, Git, Python/uv, Node/npm, existing Temporal TypeScript Worker.

## Global Constraints

- Use a contract-valid `http://127.0.0.1:<port>/engine-smoke.git` fixture URI; never pass a `file://` URI or local path across the Gateway boundary.
- Use a generated fake agent that only writes `PLAN_PATH`; do not invoke a real AI provider, credentials, or external SCM.
- Do not modify production Worker/Gateway/Temporal code or add dependencies.
- Keep fixture files and logs under the OS temporary directory; never delete user repositories or Docker volumes.
- On failure, stop only child processes started by the script and print their log paths.

## 하네스 적용

- `backend-planner` confirmed current runtime contracts; `backend-developer` implements the script and focused check.
- `backend-reviewer` reviews endpoint contracts, process cleanup, and path/secret handling.
- Verification starts the real stack, then checks Python Worker and Gateway capability endpoints plus the Temporal Worker startup log.

---

### Task 1: Local Engine stack setup script

**Files:**
- Create: `scripts/start-engine-stack.ps1`

**Interfaces:**
- Consumes: `docker-compose.dev.yml`, `apps/python-agent-worker`, `apps/worker-gateway`, `apps/temporal-worker`.
- Produces: ready endpoints at ports 8000/3001, Temporal at 7233, and a JSON connection summary with log paths.

- [ ] **Step 1: Define the failing readiness contract**

Run `pwsh -File scripts/start-engine-stack.ps1` before creating it. Expected: the script is missing.

- [ ] **Step 2: Add the minimal setup implementation**

The script starts the Temporal Docker profile, creates a bare fixture repository with a `main` commit, runs `git update-server-info`, starts the fixture HTTP server, Python Worker, Gateway, and Temporal Worker, then waits for `/v1/capabilities` on ports 8000 and 3001.

- [ ] **Step 3: Run the real setup check**

Run `pwsh -ExecutionPolicy Bypass -File scripts/start-engine-stack.ps1`, then call both capability endpoints with `curl.exe`. Expected: each reports `python-agent-worker`, `fake-agent`, and `READ`/`WRITE` modes.

- [ ] **Step 4: Commit**

Stage only this script and plan, then commit with `test: add local engine stack setup script`.
