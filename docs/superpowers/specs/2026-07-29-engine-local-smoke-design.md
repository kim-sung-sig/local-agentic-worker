# Engine Local Smoke Test Design

## Goal

Provide one Windows PowerShell command that proves the currently implemented engine path can run locally: a Temporal workflow invokes the Python Worker through the Gateway for INTAKE and PLANNING, then remains at the PLANNING approval gate.

## Scope

- Start and stop the local Temporal Docker profile and all three local processes (Git fixture server, Python Worker, Worker Gateway, Temporal Worker).
- Create a disposable bare Git repository and serve it with Python's HTTP server, so the Worker receives a contract-valid `http://127.0.0.1:<port>/engine-smoke.git` URI rather than a forbidden local path.
- Generate a deterministic local fake-agent script in the temporary directory. It writes the requested plan file but never invokes Codex, Claude, SCM, or an external provider.
- Run the existing `integration:run` driver with a generated workflow ID and assert that it reports `currentStage=PLANNING` and `status=RUNNING`.
- Probe Worker and Gateway capabilities before the workflow run and emit actionable failure output.
- Remove only the script-created temporary directory and child processes. Docker containers remain available unless the caller explicitly requests shutdown.

## Design

`scripts/test-engine-smoke.ps1` owns the process lifecycle. It creates a unique directory under the operating-system temporary directory, initializes a tiny bare Git fixture with a `main` branch, runs `git update-server-info`, and starts `python -m http.server` for that fixture. It launches the Python Worker with `WORKER_WORKSPACE_ROOT` set to a child of that temporary directory and `AGENT_COMMAND` pointing to the generated fake-agent script.

The script starts the Gateway and Temporal Worker with their existing environment contracts, waits for each HTTP/Temporal endpoint, then runs `npm run integration:run -- <generated-run-id>`. It preserves captured child-process output on failure and prints the run ID and Temporal UI URL on success.

## Non-goals

- No provider credentials, real AI agent execution, webhook/SSE work, source-control merge, or full six-stage workflow.
- No PostgreSQL ledger assertion: the current Python Worker keeps execution status in process memory and the historical `verify-ledger.sql` file no longer exists.
- No attempt to test restart durability; a separate persistence design is needed before that claim can be made.

## Acceptance Criteria

1. The script fails clearly when Docker, Git, Node/npm, Python/uv, or the required ports are unavailable.
2. It validates the Worker and Gateway capability endpoints before dispatching the workflow.
3. A successful execution prints a unique run ID and confirms the workflow is running at PLANNING.
4. A failing activity or readiness timeout exits non-zero and leaves enough logs to diagnose the failed component.
5. The script's real local smoke run is the runnable check: it also verifies the generated plan file, plan branch, and plan commit in the disposable worktree.
