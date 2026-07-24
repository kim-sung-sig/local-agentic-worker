# Agent Runtime Adapter — PLANNING Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Python worker's fake-work + PostgreSQL ledger with the real Agent Runtime adapter's PLANNING slice: clone/prepare a project, create a per-run worktree/branch, run a configurable agent command to generate a plan, commit it, and return a git reference.

**Architecture:** The worker becomes a stateless dev-environment + agent-execution host whose durable state is git (base clone + per-run worktree/branch/commit). Temporal owns idempotency/durability; the worker makes each step idempotent via git/filesystem existence checks. The `agent-worker/v1` async transport (`submit`→poll `status`/`events`) and Gateway sticky routing are reused unchanged.

**Tech Stack:** Python 3.11 + FastAPI + `uv`, `git` CLI via `subprocess`, `concurrent.futures.ThreadPoolExecutor` for background execution, `pytest` + `httpx`/`TestClient`. No database, no `psycopg`.

## Global Constraints

- Keep `agent-worker/v1` as the only execution contract boundary. Requests/responses must not include provider SDK objects, secrets, `WorkspaceRef`, or local/absolute paths — only git refs and repo-relative paths. (from design)
- Temporal owns idempotency and durability; the worker owns no database. Idempotency is derived from git/filesystem state. (from design)
- `repositoryUri` must be a credential-free remote URI (existing `ProjectExecutionSnapshot` validator rejects `file://`, userinfo, query, fragment, and bad ports). Do not weaken this validator. (from `models.py`)
- The plan artifact is a git reference `{branch, commitSha, planPath}`, serialized into `ExecutionStatus.artifactRefs[0]` as `"{branch}@{commitSha}:{planPath}"`. The plan body never crosses the contract. (from design)
- Branch name is `agent/plan/<workflowRunId>`; plan path is `docs/plans/<workflowRunId>.plan.md`. (from design)
- Agent execution is an abstract `AGENT_COMMAND` shell command line; no agent-vendor coupling in code. (from design)
- ESM/Python import and existing project conventions are followed; only files listed per task are staged (no `git add -A`; leave unrelated working-tree churn untouched).
- Out of scope: IMPLEMENTATION/QA stages, real Codex/Claude wiring, artifact store, Control-Plane approval automation, webhooks/SSE, PR creation.

---

## File Structure

- `apps/python-agent-worker/src/agent_worker/workspace.py` — git operations: `repo_key`, `ensure_base_clone`, `branch_name`, `ensure_worktree`, `plan_ref`. Owns on-disk git state under the workspace root.
- `apps/python-agent-worker/src/agent_worker/planner.py` — `generate_plan`: run `AGENT_COMMAND` in a worktree, commit the plan, return the ref; short-circuits if the plan commit already exists.
- `apps/python-agent-worker/src/agent_worker/jobs.py` — `WorkerConfig`, `PlanningJobs`: async submit + `status`/`events` projection; serializes the git ref into `artifactRefs`.
- `apps/python-agent-worker/src/agent_worker/app.py` — FastAPI handlers for `agent-worker/v1`, wiring `PlanningJobs`. (rewritten)
- `apps/python-agent-worker/src/agent_worker/models.py` — unchanged (contract models + validation).
- Deleted: `ledger.py`, `migrations/`. `pyproject.toml` drops `psycopg`.
- `apps/python-agent-worker/tests/test_api.py` — rewritten: PLANNING-slice app tests + preserved contract-matrix tests.
- `docker-compose.dev.yml` — remove the `agent-worker-postgres` (ledger) service; keep the Temporal stack.
- `dev/verify-ledger.sql` — deleted (obsolete).

---

## Task 1: `workspace.py` — git base clone + per-run worktree

**Files:**
- Create: `apps/python-agent-worker/src/agent_worker/workspace.py`
- Test: `apps/python-agent-worker/tests/test_workspace.py`

**Interfaces:**
- Consumes: `git` CLI; a workspace root `Path`.
- Produces:
  - `repo_key(repository_uri: str) -> str`
  - `branch_name(workflow_run_id: str) -> str` → `"agent/plan/<id>"`
  - `PLAN_RELPATH_TEMPLATE = "docs/plans/{workflow_run_id}.plan.md"`
  - `ensure_base_clone(workspace_root: Path, repository_uri: str) -> Path` (clone if missing else fetch; returns base clone path)
  - `ensure_worktree(base: Path, workspace_root: Path, workflow_run_id: str, base_branch: str, requested_commit: str | None) -> Path`
  - `plan_ref(worktree: Path, workflow_run_id: str) -> dict | None` → `{"branch","commitSha","planPath"}` or `None`

- [ ] **Step 1: Write the failing test**

Create `apps/python-agent-worker/tests/test_workspace.py`:

```python
import subprocess
from pathlib import Path

import pytest

from agent_worker import workspace


def _run(args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


@pytest.fixture
def remote(tmp_path):
    bare = tmp_path / "remote.git"
    _run(["git", "init", "--bare", "-b", "main", str(bare)])
    seed = tmp_path / "seed"
    _run(["git", "clone", str(bare), str(seed)])
    (seed / "README.md").write_text("seed\n", encoding="utf-8")
    _run(["git", "-C", str(seed), "add", "-A"])
    _run(["git", "-C", str(seed), "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "seed"])
    _run(["git", "-C", str(seed), "push", "origin", "main"])
    return bare.as_uri()  # file:// URI usable directly by git clone in tests


def test_ensure_base_clone_creates_then_reuses(tmp_path, remote):
    root = tmp_path / "ws"
    base = workspace.ensure_base_clone(root, remote)
    assert (base / ".git").exists()
    # Second call must not fail and must reuse the same directory (fetch path).
    base_again = workspace.ensure_base_clone(root, remote)
    assert base_again == base


def test_ensure_worktree_creates_branch_then_reuses(tmp_path, remote):
    root = tmp_path / "ws"
    base = workspace.ensure_base_clone(root, remote)
    worktree = workspace.ensure_worktree(base, root, "run-1", "main", None)
    assert (worktree / ".git").exists()
    branch = subprocess.run(
        ["git", "-C", str(worktree), "rev-parse", "--abbrev-ref", "HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
    assert branch == "agent/plan/run-1"
    assert workspace.ensure_worktree(base, root, "run-1", "main", None) == worktree
    assert workspace.plan_ref(worktree, "run-1") is None  # no plan committed yet
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/python-agent-worker && uv run pytest tests/test_workspace.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'agent_worker.workspace'`.

- [ ] **Step 3: Write minimal implementation**

Create `apps/python-agent-worker/src/agent_worker/workspace.py`:

```python
import hashlib
import subprocess
from pathlib import Path

PLAN_RELPATH_TEMPLATE = "docs/plans/{workflow_run_id}.plan.md"


def repo_key(repository_uri: str) -> str:
    return hashlib.sha256(repository_uri.encode("utf-8")).hexdigest()[:16]


def branch_name(workflow_run_id: str) -> str:
    return f"agent/plan/{workflow_run_id}"


def _git(args: list[str], cwd: Path) -> str:
    result = subprocess.run(["git", *args], cwd=str(cwd), capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def ensure_base_clone(workspace_root: Path, repository_uri: str) -> Path:
    base = Path(workspace_root) / "base" / repo_key(repository_uri)
    if (base / ".git").exists():
        _git(["fetch", "--all", "--prune"], cwd=base)
        return base
    base.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(["git", "clone", repository_uri, str(base)], capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"git clone failed: {result.stderr.strip()}")
    return base


def ensure_worktree(base: Path, workspace_root: Path, workflow_run_id: str, base_branch: str, requested_commit: str | None) -> Path:
    worktree = Path(workspace_root) / "worktrees" / workflow_run_id
    if (worktree / ".git").exists():
        return worktree
    worktree.parent.mkdir(parents=True, exist_ok=True)
    start_point = requested_commit or f"origin/{base_branch}"
    _git(["worktree", "add", "-b", branch_name(workflow_run_id), str(worktree), start_point], cwd=base)
    return worktree


def plan_ref(worktree: Path, workflow_run_id: str) -> dict | None:
    plan_path = PLAN_RELPATH_TEMPLATE.format(workflow_run_id=workflow_run_id)
    probe = subprocess.run(["git", "cat-file", "-e", f"HEAD:{plan_path}"], cwd=str(worktree), capture_output=True)
    if probe.returncode != 0:
        return None
    return {"branch": branch_name(workflow_run_id), "commitSha": _git(["rev-parse", "HEAD"], cwd=worktree), "planPath": plan_path}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/python-agent-worker && uv run pytest tests/test_workspace.py -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/python-agent-worker/src/agent_worker/workspace.py apps/python-agent-worker/tests/test_workspace.py
git commit -m "feat: add worker git workspace (base clone + per-run worktree)"
```

---

## Task 2: `planner.py` — agent subprocess + commit (idempotent)

**Files:**
- Create: `apps/python-agent-worker/src/agent_worker/planner.py`
- Test: `apps/python-agent-worker/tests/test_planner.py`

**Interfaces:**
- Consumes: `workspace.plan_ref`, `workspace.PLAN_RELPATH_TEMPLATE`, `workspace.branch_name`; `git` CLI.
- Produces: `generate_plan(worktree: Path, workflow_run_id: str, agent_command: str, timeout_seconds: int) -> dict` → the git ref `{"branch","commitSha","planPath"}`. Runs `agent_command` (shell) with env `WORKFLOW_RUN_ID` and `PLAN_PATH`; returns the existing ref without re-running if the plan commit already exists.

- [ ] **Step 1: Write the failing test**

Create `apps/python-agent-worker/tests/test_planner.py`:

```python
import subprocess
import sys
from pathlib import Path

import pytest

from agent_worker import planner, workspace


def _run(args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


@pytest.fixture
def worktree(tmp_path):
    bare = tmp_path / "remote.git"
    _run(["git", "init", "--bare", "-b", "main", str(bare)])
    seed = tmp_path / "seed"
    _run(["git", "clone", str(bare), str(seed)])
    (seed / "README.md").write_text("seed\n", encoding="utf-8")
    _run(["git", "-C", str(seed), "add", "-A"])
    _run(["git", "-C", str(seed), "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "seed"])
    _run(["git", "-C", str(seed), "push", "origin", "main"])
    root = tmp_path / "ws"
    base = workspace.ensure_base_clone(root, bare.as_uri())
    return workspace.ensure_worktree(base, root, "run-1", "main", None)


@pytest.fixture
def agent_command(tmp_path):
    # A portable fake agent: writes the plan file at $PLAN_PATH and bumps a counter file.
    counter = tmp_path / "invocations.txt"
    script = tmp_path / "fake_agent.py"
    script.write_text(
        "import os, pathlib\n"
        f"c = pathlib.Path(r'{counter}')\n"
        "c.write_text(str(int(c.read_text()) + 1) if c.exists() else '1')\n"
        "p = pathlib.Path(os.environ['PLAN_PATH'])\n"
        "p.parent.mkdir(parents=True, exist_ok=True)\n"
        "p.write_text('# Plan for ' + os.environ['WORKFLOW_RUN_ID'] + '\\n')\n",
        encoding="utf-8",
    )
    command = f'"{sys.executable}" "{script}"'
    return command, counter


def test_generate_plan_runs_agent_commits_and_returns_ref(worktree, agent_command):
    command, counter = agent_command
    ref = planner.generate_plan(worktree, "run-1", command, timeout_seconds=30)
    assert ref["branch"] == "agent/plan/run-1"
    assert ref["planPath"] == "docs/plans/run-1.plan.md"
    assert len(ref["commitSha"]) == 40
    assert counter.read_text() == "1"
    # Idempotent: second call must NOT re-run the agent and must return the same commit.
    ref_again = planner.generate_plan(worktree, "run-1", command, timeout_seconds=30)
    assert ref_again == ref
    assert counter.read_text() == "1"


def test_generate_plan_fails_when_agent_produces_no_plan(worktree):
    noop = f'"{sys.executable}" -c "pass"'
    with pytest.raises(RuntimeError, match="did not produce"):
        planner.generate_plan(worktree, "run-1", noop, timeout_seconds=30)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/python-agent-worker && uv run pytest tests/test_planner.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'agent_worker.planner'`.

- [ ] **Step 3: Write minimal implementation**

Create `apps/python-agent-worker/src/agent_worker/planner.py`:

```python
import os
import subprocess
from pathlib import Path

from .workspace import PLAN_RELPATH_TEMPLATE, plan_ref


def generate_plan(worktree: Path, workflow_run_id: str, agent_command: str, timeout_seconds: int) -> dict:
    existing = plan_ref(worktree, workflow_run_id)
    if existing is not None:
        return existing

    plan_relpath = PLAN_RELPATH_TEMPLATE.format(workflow_run_id=workflow_run_id)
    plan_file = Path(worktree) / plan_relpath
    plan_file.parent.mkdir(parents=True, exist_ok=True)

    env = {**os.environ, "WORKFLOW_RUN_ID": workflow_run_id, "PLAN_PATH": str(plan_file)}
    result = subprocess.run(agent_command, cwd=str(worktree), shell=True, env=env, capture_output=True, text=True, timeout=timeout_seconds)
    if result.returncode != 0:
        raise RuntimeError(f"agent command failed: {result.stderr.strip()}")
    if not plan_file.exists():
        raise RuntimeError("agent command did not produce the plan file")

    _commit(worktree, plan_relpath, f"plan: {workflow_run_id}")
    ref = plan_ref(worktree, workflow_run_id)
    if ref is None:
        raise RuntimeError("plan commit was not created")
    return ref


def _commit(worktree: Path, plan_relpath: str, message: str) -> None:
    add = subprocess.run(["git", "add", plan_relpath], cwd=str(worktree), capture_output=True, text=True)
    if add.returncode != 0:
        raise RuntimeError(f"git add failed: {add.stderr.strip()}")
    commit = subprocess.run(
        ["git", "-c", "user.email=agent@worker.local", "-c", "user.name=agent-worker", "commit", "-m", message],
        cwd=str(worktree), capture_output=True, text=True,
    )
    if commit.returncode != 0:
        raise RuntimeError(f"git commit failed: {commit.stderr.strip()}")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/python-agent-worker && uv run pytest tests/test_planner.py -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/python-agent-worker/src/agent_worker/planner.py apps/python-agent-worker/tests/test_planner.py
git commit -m "feat: add idempotent plan generation via agent command"
```

---

## Task 3: `jobs.py` — async submit + status/event projection

**Files:**
- Create: `apps/python-agent-worker/src/agent_worker/jobs.py`
- Test: `apps/python-agent-worker/tests/test_jobs.py`

**Interfaces:**
- Consumes: `workspace.ensure_base_clone`, `workspace.ensure_worktree`; `planner.generate_plan`; `models.ExecutionSubmission`.
- Produces:
  - `@dataclass WorkerConfig(workspace_root: Path, agent_command: str, agent_timeout_seconds: int = 600)`
  - `class PlanningJobs(config: WorkerConfig)` with:
    - `submit(submission: ExecutionSubmission) -> str` (returns `execution_id == submission.idempotencyKey`; schedules background work; idempotent for a known id)
    - `status(execution_id: str) -> dict | None` → `{"executionId","status","terminal","artifactRefs"}`
    - `events(execution_id: str, after: int) -> list[dict] | None` → list of `{"executionId","cursor","type","data"}`
    - `shutdown() -> None`
  - Ref serialization: `artifactRefs = ["{branch}@{commitSha}:{planPath}"]` on completion.

- [ ] **Step 1: Write the failing test**

Create `apps/python-agent-worker/tests/test_jobs.py`:

```python
import subprocess
import sys
import time
from pathlib import Path

import pytest

from agent_worker.jobs import PlanningJobs, WorkerConfig
from agent_worker.models import ExecutionSubmission


def _run(args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


def _submission(run_id: str, remote_uri: str) -> ExecutionSubmission:
    return ExecutionSubmission.model_validate({
        "contractVersion": "agent-worker/v1",
        "idempotencyKey": f"{run_id}:PLANNING:1:1",
        "workflowRunId": run_id,
        "stage": "PLANNING",
        "attemptNumber": 1,
        "stageExecutionGeneration": 1,
        "adapterId": "fake-agent",
        "project": {"projectId": "p1", "repositoryUri": remote_uri, "baseBranch": "main", "credentialRef": None, "requestedSourceCommit": None},
        "mode": "READ",
    })


@pytest.fixture
def config(tmp_path):
    counter = tmp_path / "invocations.txt"
    script = tmp_path / "fake_agent.py"
    script.write_text(
        "import os, pathlib\n"
        f"c = pathlib.Path(r'{counter}')\n"
        "c.write_text(str(int(c.read_text()) + 1) if c.exists() else '1')\n"
        "p = pathlib.Path(os.environ['PLAN_PATH'])\n"
        "p.parent.mkdir(parents=True, exist_ok=True)\n"
        "p.write_text('# Plan ' + os.environ['WORKFLOW_RUN_ID'])\n",
        encoding="utf-8",
    )
    return WorkerConfig(workspace_root=tmp_path / "ws", agent_command=f'"{sys.executable}" "{script}"', agent_timeout_seconds=30), counter


@pytest.fixture
def remote(tmp_path):
    bare = tmp_path / "remote.git"
    _run(["git", "init", "--bare", "-b", "main", str(bare)])
    seed = tmp_path / "seed"
    _run(["git", "clone", str(bare), str(seed)])
    (seed / "README.md").write_text("seed\n", encoding="utf-8")
    _run(["git", "-C", str(seed), "add", "-A"])
    _run(["git", "-C", str(seed), "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "seed"])
    _run(["git", "-C", str(seed), "push", "origin", "main"])
    return bare.as_uri()


def _await_complete(jobs, execution_id, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        status = jobs.status(execution_id)
        if status and status["terminal"]:
            return status
        time.sleep(0.1)
    raise AssertionError(f"execution {execution_id} did not terminate; last={jobs.status(execution_id)}")


def test_submit_runs_planning_and_projects_completed_ref(config, remote):
    cfg, _counter = config
    jobs = PlanningJobs(cfg)
    try:
        execution_id = jobs.submit(_submission("run-1", remote))
        assert execution_id == "run-1:PLANNING:1:1"
        status = _await_complete(jobs, execution_id)
        assert status["status"] == "COMPLETED" and status["terminal"] is True
        assert len(status["artifactRefs"]) == 1
        assert status["artifactRefs"][0].startswith("agent/plan/run-1@")
        assert status["artifactRefs"][0].endswith(":docs/plans/run-1.plan.md")
        events = jobs.events(execution_id, 0)
        assert [e["type"] for e in events] == ["accepted", "running", "completed"]
        assert [e["cursor"] for e in events] == [1, 2, 3]
    finally:
        jobs.shutdown()


def test_resubmit_is_idempotent_without_rerunning_agent(config, remote):
    cfg, counter = config
    jobs = PlanningJobs(cfg)
    try:
        first = jobs.submit(_submission("run-2", remote))
        _await_complete(jobs, first)
        assert counter.read_text() == "1"
        again = jobs.submit(_submission("run-2", remote))
        assert again == first
        assert counter.read_text() == "1"  # not re-run
    finally:
        jobs.shutdown()


def test_fresh_instance_reuses_git_state_across_restart(config, remote):
    cfg, counter = config
    jobs = PlanningJobs(cfg)
    try:
        _await_complete(jobs, jobs.submit(_submission("run-3", remote)))
        assert counter.read_text() == "1"
    finally:
        jobs.shutdown()
    # Simulate a worker restart: fresh in-memory state, same workspace on disk.
    restarted = PlanningJobs(cfg)
    try:
        status = _await_complete(restarted, restarted.submit(_submission("run-3", remote)))
        assert status["status"] == "COMPLETED"
        assert counter.read_text() == "1"  # git-level idempotency: agent not re-run
    finally:
        restarted.shutdown()


def test_status_and_events_are_none_for_unknown_execution(config):
    cfg, _counter = config
    jobs = PlanningJobs(cfg)
    try:
        assert jobs.status("nope:PLANNING:1:1") is None
        assert jobs.events("nope:PLANNING:1:1", 0) is None
    finally:
        jobs.shutdown()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/python-agent-worker && uv run pytest tests/test_jobs.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'agent_worker.jobs'`.

- [ ] **Step 3: Write minimal implementation**

Create `apps/python-agent-worker/src/agent_worker/jobs.py`:

```python
import threading
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

from . import planner, workspace
from .models import ExecutionSubmission

_TERMINAL = {"COMPLETED", "FAILED", "CANCELLED"}


@dataclass
class WorkerConfig:
    workspace_root: Path
    agent_command: str
    agent_timeout_seconds: int = 600


@dataclass
class _Job:
    execution_id: str
    workflow_run_id: str
    status: str = "ACCEPTED"
    artifact_refs: list[str] = field(default_factory=list)
    events: list[dict] = field(default_factory=lambda: [{"cursor": 1, "type": "accepted", "data": {}}])


class PlanningJobs:
    def __init__(self, config: WorkerConfig):
        self._config = config
        self._jobs: dict[str, _Job] = {}
        self._lock = threading.Lock()
        self._executor = ThreadPoolExecutor(max_workers=4)

    def submit(self, submission: ExecutionSubmission) -> str:
        execution_id = submission.idempotencyKey
        with self._lock:
            if execution_id in self._jobs:
                return execution_id
            self._jobs[execution_id] = _Job(execution_id=execution_id, workflow_run_id=submission.workflowRunId)
        self._executor.submit(self._run, execution_id, submission)
        return execution_id

    def _run(self, execution_id: str, submission: ExecutionSubmission) -> None:
        try:
            self._append(execution_id, "running", "RUNNING")
            base = workspace.ensure_base_clone(self._config.workspace_root, submission.project.repositoryUri)
            worktree = workspace.ensure_worktree(
                base, self._config.workspace_root, submission.workflowRunId,
                submission.project.baseBranch, submission.project.requestedSourceCommit,
            )
            ref = planner.generate_plan(worktree, submission.workflowRunId, self._config.agent_command, self._config.agent_timeout_seconds)
            serialized = f"{ref['branch']}@{ref['commitSha']}:{ref['planPath']}"
            with self._lock:
                job = self._jobs[execution_id]
                job.artifact_refs = [serialized]
            self._append(execution_id, "completed", "COMPLETED", data={"ref": serialized})
        except Exception as exc:  # noqa: BLE001 - surface any failure as a terminal failed event
            self._append(execution_id, "failed", "FAILED", data={"error": str(exc)})

    def _append(self, execution_id: str, event_type: str, status: str, data: dict | None = None) -> None:
        with self._lock:
            job = self._jobs[execution_id]
            job.status = status
            job.events.append({"cursor": len(job.events) + 1, "type": event_type, "data": data or {}})

    def status(self, execution_id: str) -> dict | None:
        with self._lock:
            job = self._jobs.get(execution_id)
            if job is None:
                return None
            return {"executionId": execution_id, "status": job.status, "terminal": job.status in _TERMINAL, "artifactRefs": list(job.artifact_refs)}

    def events(self, execution_id: str, after: int) -> list[dict] | None:
        with self._lock:
            job = self._jobs.get(execution_id)
            if job is None:
                return None
            return [{"executionId": execution_id, **event} for event in job.events if event["cursor"] > after]

    def shutdown(self) -> None:
        self._executor.shutdown(wait=True)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/python-agent-worker && uv run pytest tests/test_jobs.py -v`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/python-agent-worker/src/agent_worker/jobs.py apps/python-agent-worker/tests/test_jobs.py
git commit -m "feat: add async planning job registry with git-derived idempotency"
```

---

## Task 4: `app.py` rewrite + remove the ledger

**Files:**
- Modify (rewrite): `apps/python-agent-worker/src/agent_worker/app.py`
- Delete: `apps/python-agent-worker/src/agent_worker/ledger.py`
- Delete: `apps/python-agent-worker/migrations/0001_agent_worker_ledger.sql` (and the `migrations/` dir)
- Modify: `apps/python-agent-worker/pyproject.toml` (drop `psycopg`)
- Modify (rewrite): `apps/python-agent-worker/tests/test_api.py`

**Interfaces:**
- Consumes: `jobs.PlanningJobs`, `jobs.WorkerConfig`; `models` contract classes.
- Produces: `create_app(config: WorkerConfig | None = None) -> FastAPI` and module-level `app = create_app()`. Endpoints: `POST /v1/executions`, `GET /v1/executions/{id}`, `GET /v1/executions/{id}/events`, `POST /v1/executions/{id}:cancel` (409), `GET /v1/capabilities`. Env config: `WORKER_WORKSPACE_ROOT`, `AGENT_COMMAND`, `AGENT_TIMEOUT_SECONDS` (default 600).

- [ ] **Step 1: Write the failing test**

Rewrite `apps/python-agent-worker/tests/test_api.py`. Keep the pure-model contract tests at the bottom verbatim (they need no database); replace everything that used `postgres_url`/`Ledger`. New content:

```python
import subprocess
import sys
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from agent_worker.app import create_app
from agent_worker.jobs import WorkerConfig
from agent_worker.models import Submission, WorkerCapabilities


def payload(key: str, repository_uri: str = "https://github.com/example/repository.git") -> dict:
    workflow_run_id, stage, attempt_number, generation = key.split(":")
    return {
        "contractVersion": "agent-worker/v1",
        "idempotencyKey": key,
        "workflowRunId": workflow_run_id,
        "stage": stage,
        "attemptNumber": int(attempt_number),
        "stageExecutionGeneration": int(generation),
        "adapterId": "fake-agent",
        "project": {"projectId": "project-1", "repositoryUri": repository_uri, "baseBranch": "main", "credentialRef": None, "requestedSourceCommit": None},
        "mode": "READ",
    }


def _run(args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


@pytest.fixture
def worker(tmp_path, monkeypatch):
    # Local bare repo acts as the remote; git `insteadOf` maps the https URI to it,
    # so the contract-valid URI resolves to local disk with no network.
    bare = tmp_path / "remote.git"
    _run(["git", "init", "--bare", "-b", "main", str(bare)])
    seed = tmp_path / "seed"
    _run(["git", "clone", str(bare), str(seed)])
    (seed / "README.md").write_text("seed\n", encoding="utf-8")
    _run(["git", "-C", str(seed), "add", "-A"])
    _run(["git", "-C", str(seed), "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "seed"])
    _run(["git", "-C", str(seed), "push", "origin", "main"])

    uri = "https://github.com/example/repository.git"
    gitconfig = tmp_path / "gitconfig"
    _run(["git", "config", "--file", str(gitconfig), f"url.{bare.as_uri()}.insteadOf", uri])
    monkeypatch.setenv("GIT_CONFIG_GLOBAL", str(gitconfig))

    counter = tmp_path / "invocations.txt"
    script = tmp_path / "fake_agent.py"
    script.write_text(
        "import os, pathlib\n"
        f"c = pathlib.Path(r'{counter}')\n"
        "c.write_text(str(int(c.read_text()) + 1) if c.exists() else '1')\n"
        "p = pathlib.Path(os.environ['PLAN_PATH'])\n"
        "p.parent.mkdir(parents=True, exist_ok=True)\n"
        "p.write_text('# Plan ' + os.environ['WORKFLOW_RUN_ID'])\n",
        encoding="utf-8",
    )
    config = WorkerConfig(workspace_root=tmp_path / "ws", agent_command=f'"{sys.executable}" "{script}"', agent_timeout_seconds=30)
    return create_app(config), uri, counter


def _await(client, execution_id, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        body = client.get(f"/v1/executions/{execution_id}").json()
        if body["terminal"]:
            return body
        time.sleep(0.1)
    raise AssertionError(f"{execution_id} did not terminate")


def test_planning_submit_poll_returns_git_ref_without_leaking_paths(worker):
    app, uri, _counter = worker
    with TestClient(app) as client:
        created = client.post("/v1/executions", json=payload("run-1:PLANNING:1:1", uri))
        assert created.status_code == 200
        execution_id = created.json()["executionId"]
        status = _await(client, execution_id)
        assert status["status"] == "COMPLETED"
        assert status["artifactRefs"][0].startswith("agent/plan/run-1@")
        events = client.get(f"/v1/executions/{execution_id}/events").json()
        assert [e["type"] for e in events] == ["accepted", "running", "completed"]
        # No absolute host path or secret-like content in any response.
        blob = created.text + client.get(f"/v1/executions/{execution_id}").text + client.get(f"/v1/executions/{execution_id}/events").text
        assert "C:\\" not in blob and "file://" not in blob
        assert client.post(f"/v1/executions/{execution_id}:cancel").status_code == 409
        assert client.get("/v1/capabilities").json() == {"workerId": "python-agent-worker", "adapterIds": ["fake-agent"], "modes": ["READ", "WRITE"]}


def test_resubmit_is_idempotent(worker):
    app, uri, counter = worker
    with TestClient(app) as client:
        first = client.post("/v1/executions", json=payload("run-2:PLANNING:1:1", uri)).json()["executionId"]
        _await(client, first)
        assert counter.read_text() == "1"
        again = client.post("/v1/executions", json=payload("run-2:PLANNING:1:1", uri)).json()["executionId"]
        assert again == first
        assert counter.read_text() == "1"


def test_unknown_execution_is_404(worker):
    app, _uri, _counter = worker
    with TestClient(app) as client:
        assert client.get("/v1/executions/missing:PLANNING:1:1").status_code == 404


def test_missing_config_fails_on_startup(monkeypatch):
    monkeypatch.delenv("WORKER_WORKSPACE_ROOT", raising=False)
    monkeypatch.delenv("AGENT_COMMAND", raising=False)
    with pytest.raises(RuntimeError, match="WORKER_WORKSPACE_ROOT"):
        with TestClient(create_app()):
            pass


# --- preserved contract-model tests (no app, no database) ---

def test_submission_contract_accepts_remote_uris():
    for uri in ("https://github.com/example/repository.git", "ssh://github.com/example/repository.git"):
        valid = payload("run-1:PLANNING:1:1")
        valid["project"]["repositoryUri"] = uri
        assert Submission.model_validate(valid).project.repositoryUri == uri


def test_worker_capabilities_reject_empty_adapter_id():
    with pytest.raises(Exception):
        WorkerCapabilities.model_validate({"workerId": "worker", "adapterIds": [""], "modes": ["READ"]})


@pytest.mark.parametrize("mutate", [
    lambda v: v["project"].update(repositoryUri="file:///tmp/repository"),
    lambda v: v["project"].update(repositoryUri="https://user:password@github.com/example/repository.git"),
    lambda v: v["project"].update(repositoryUri="https://github.com/example/repository.git?token=x"),
    lambda v: v["project"].update(repositoryUri="https://github.com/example/repository.git#secret"),
    lambda v: v["project"].update(repositoryUri="https://github.com:invalid/repository.git"),
    lambda v: v.update(adapterId=""),
    lambda v: v.update(attemptNumber="1"),
    lambda v: v.update(attemptNumber=0),
    lambda v: v.update(stage="UNKNOWN"),
    lambda v: v.update(extra="value"),
    lambda v: v["project"].update(token="value"),
    lambda v: v["project"].update(secret="value"),
    lambda v: v["project"].update(baseBranch="/absolute/path"),
    lambda v: v["project"].update(baseBranch=r"C:\\local"),
    lambda v: v["project"].update(baseBranch=r"\\server\share"),
    lambda v: v.update(idempotencyKey="not-the-formula"),
])
def test_submission_contract_matrix_rejects_invalid_values(mutate):
    invalid = payload("run-1:PLANNING:1:1")
    mutate(invalid)
    with pytest.raises(Exception):
        Submission.model_validate(invalid)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/python-agent-worker && uv run pytest tests/test_api.py -v`
Expected: FAIL — `create_app` still imports `ledger` / signature mismatch, and `WorkerConfig` import resolves but `create_app(config)` behavior differs.

- [ ] **Step 3: Rewrite `app.py`**

Replace `apps/python-agent-worker/src/agent_worker/app.py` entirely with:

```python
import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query

from .jobs import PlanningJobs, WorkerConfig
from .models import ExecutionEvent, ExecutionResult, ExecutionStatus, ExecutionSubmission, WorkerCapabilities


def _config_from_env() -> WorkerConfig:
    root = os.environ.get("WORKER_WORKSPACE_ROOT")
    command = os.environ.get("AGENT_COMMAND")
    if not root or not command:
        raise RuntimeError("WORKER_WORKSPACE_ROOT and AGENT_COMMAND are required")
    return WorkerConfig(workspace_root=Path(root), agent_command=command, agent_timeout_seconds=int(os.environ.get("AGENT_TIMEOUT_SECONDS", "600")))


def create_app(config: WorkerConfig | None = None) -> FastAPI:
    jobs: PlanningJobs | None = None

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        nonlocal jobs
        jobs = PlanningJobs(config or _config_from_env())
        try:
            yield
        finally:
            jobs.shutdown()

    def current() -> PlanningJobs:
        if jobs is None:
            raise RuntimeError("worker is unavailable")
        return jobs

    app = FastAPI(lifespan=lifespan)

    @app.post("/v1/executions", response_model=ExecutionResult)
    def submit(submission: ExecutionSubmission):
        return {"executionId": current().submit(submission)}

    @app.get("/v1/executions/{execution_id}", response_model=ExecutionStatus)
    def status(execution_id: str):
        result = current().status(execution_id)
        if result is None:
            raise HTTPException(404, "execution not found")
        return result

    @app.get("/v1/executions/{execution_id}/events", response_model=list[ExecutionEvent])
    def events(execution_id: str, after: int = Query(0, ge=0)):
        result = current().events(execution_id, after)
        if result is None:
            raise HTTPException(404, "execution not found")
        return result

    @app.post("/v1/executions/{execution_id}:cancel")
    def cancel(execution_id: str):
        if current().status(execution_id) is None:
            raise HTTPException(404, "execution not found")
        raise HTTPException(409, "cancellation unsupported for planning executions")

    @app.get("/v1/capabilities", response_model=WorkerCapabilities)
    def capabilities():
        return {"workerId": "python-agent-worker", "adapterIds": ["fake-agent"], "modes": ["READ", "WRITE"]}

    return app


app = create_app()
```

- [ ] **Step 4: Delete the ledger and its migration; drop psycopg**

```bash
git rm apps/python-agent-worker/src/agent_worker/ledger.py
git rm apps/python-agent-worker/migrations/0001_agent_worker_ledger.sql
```

Edit `apps/python-agent-worker/pyproject.toml` — change the `dependencies` line to remove `psycopg`:

```toml
dependencies = ["fastapi>=0.115", "uvicorn>=0.30"]
```

Then refresh the lock: `cd apps/python-agent-worker && uv sync`

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd apps/python-agent-worker && uv run pytest -v`
Expected: PASS — `test_api.py`, `test_workspace.py`, `test_planner.py`, `test_jobs.py` all green; no import of `ledger` remains.

- [ ] **Step 6: Commit**

```bash
git add apps/python-agent-worker/src/agent_worker/app.py apps/python-agent-worker/tests/test_api.py apps/python-agent-worker/pyproject.toml apps/python-agent-worker/uv.lock
git commit -m "feat: serve planning slice from git workspace, remove ledger"
```

---

## Task 5: Cleanup — compose, obsolete SQL, and cross-workspace checks

**Files:**
- Modify: `docker-compose.dev.yml` (remove the `agent-worker-postgres` ledger service)
- Delete: `dev/verify-ledger.sql`
- Verify only (no change expected): `apps/worker-gateway`, `apps/temporal-worker` TS suites

**Interfaces:**
- Consumes: nothing new.
- Produces: a dev stack where the worker needs no database; the Temporal profile is unchanged.

- [ ] **Step 1: Remove the ledger Postgres service**

Edit `docker-compose.dev.yml`: delete the top-level `postgres:` service block (the `agent-worker-postgres` container, lines under `services:` for `postgres`). Keep `temporal-postgresql`, `temporal`, `temporal-ui`, and the `volumes:` block. Update the header comment's first usage line to:

```
#   docker compose -f docker-compose.dev.yml --profile temporal up -d   # Temporal server + UI
```

- [ ] **Step 2: Validate the compose still parses**

Run: `docker compose -f docker-compose.dev.yml --profile temporal config -q`
Expected: exits 0 (no output).

- [ ] **Step 3: Delete the obsolete ledger SQL**

```bash
git rm dev/verify-ledger.sql
```

- [ ] **Step 4: Confirm the TypeScript suites are unaffected**

The Gateway and Temporal adapter mock the worker, so removing the Python ledger changes nothing for them. Run:
```bash
cd apps/worker-gateway && npm run typecheck && npx vitest run
cd ../temporal-worker && npm run typecheck && npx vitest run
```
Expected: both typecheck clean; gateway 1 file / temporal suites pass. (The pre-existing `agent-worker-workflow.test.ts` ephemeral-server failure, if the local Temporal test server is unavailable, is unrelated — note it, do not fix here.)

- [ ] **Step 5: Commit**

```bash
git add docker-compose.dev.yml dev/verify-ledger.sql
git commit -m "chore: drop worker ledger postgres and obsolete verification sql"
```

---

## Task 6: End-to-end local integration run (PLANNING via git ref)

Deliverable: the full stack (Temporal → Gateway → Python Worker) driving an INTAKE→PLANNING run, with the plan returned as a git ref and the worktree/branch/commit verified on disk. Runbook task; the "test" is the observed output. Replaces the old ledger-based verification.

**Prerequisites:** Docker + Temporal profile up (`docker compose -f docker-compose.dev.yml --profile temporal up -d`), `uv`, Node deps installed, `git` on PATH, and a **git remote the worker can clone**. For a fully local run, host a bare repo over `git://` (passes the contract validator) or point at a real reachable https repo. This runbook uses a local `git daemon`.

- [ ] **Step 1: Publish a local bare repo over git://**

```bash
mkdir -p /tmp/agentic-remote && git init --bare -b main /tmp/agentic-remote/repository.git
git clone /tmp/agentic-remote/repository.git /tmp/agentic-seed && cd /tmp/agentic-seed && echo seed > README.md && git add -A && git -c user.email=t@t -c user.name=t commit -m seed && git push origin main && cd -
git daemon --reuseaddr --base-path=/tmp/agentic-remote --export-all --port=9418
```
Run `git daemon` in its own terminal. The clone URI is `git://127.0.0.1/repository.git` (scheme+host, no userinfo/query — passes `ProjectExecutionSnapshot` validation).

- [ ] **Step 2: Start the Python Worker** (PowerShell)

```powershell
cd apps/python-agent-worker
$env:WORKER_WORKSPACE_ROOT = "$env:TEMP\agent-workspace"
$env:AGENT_COMMAND = 'python -c "import os,pathlib;p=pathlib.Path(os.environ[''PLAN_PATH'']);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(''# plan ''+os.environ[''WORKFLOW_RUN_ID''])"'
$env:AGENT_TIMEOUT_SECONDS = "60"
uv run uvicorn agent_worker.app:app --app-dir src --port 8000
```
Verify: `curl http://localhost:8000/v1/capabilities` → the capabilities JSON.

- [ ] **Step 3: Start Gateway and Temporal Worker** (two PowerShell terminals)

```powershell
cd apps/worker-gateway; $env:PYTHON_WORKER_URL = "http://localhost:8000"; $env:PORT = "3001"; npm start
```
```powershell
cd apps/temporal-worker; $env:GATEWAY_URL = "http://localhost:3001"; $env:TEMPORAL_ADDRESS = "localhost:7233"; npm run start:worker
```
Expected: Gateway serves on 3001; Temporal Worker logs `Worker state changed … RUNNING`.

Note: the Temporal PLANNING adapter builds the submission's `project` from a fixed snapshot in `apps/temporal-worker/src/main.ts`. For this run, set that snapshot's `repositoryUri` to `git://127.0.0.1/repository.git` and `baseBranch` to `main` before starting the worker (edit the `project` literal in `main.ts`). Restart the worker if already running.

- [ ] **Step 4: Drive one INTAKE→PLANNING run**

```powershell
cd apps/temporal-worker; $env:TEMPORAL_ADDRESS = "localhost:7233"; npm run integration:run -- planning-itest-001
```
Expected: prints `{"runId":"planning-itest-001","currentStage":"PLANNING","status":"RUNNING"}` (parks at the PLANNING gate).

- [ ] **Step 5: Verify the plan git ref and on-disk worktree**

Confirm the worker returned a git ref (check the Python worker log for the `POST /v1/executions` and the completed status), then verify the worktree/branch/commit exist:
```bash
ls "$TEMP/agent-workspace/worktrees/planning-itest-001/docs/plans/planning-itest-001.plan.md"
git -C "$TEMP/agent-workspace/worktrees/planning-itest-001" log --oneline -1
git -C "$TEMP/agent-workspace/worktrees/planning-itest-001" rev-parse --abbrev-ref HEAD
```
Expected: the plan file exists; the latest commit message is `plan: planning-itest-001`; the branch is `agent/plan/planning-itest-001`. No `agent_worker` database is involved anywhere.

- [ ] **Step 6: Record the evidence**

Write the observed driver output, the git ref, and the three `git` verification outputs into `_workspace_gateway/25_planning_slice_run.md` (append-only; do not modify prior artifacts). Run superpowers:verification-before-completion before declaring done.

```bash
git add _workspace_gateway/25_planning_slice_run.md
git commit -m "docs: record planning slice local integration run"
```

- [ ] **Step 7: Tear down**

Stop `git daemon`, the Python Worker, Gateway, and Temporal Worker (Ctrl+C). `docker compose -f docker-compose.dev.yml --profile temporal down` when finished.

---

## Self-Review

**1. Spec coverage:**
- "Worker clones/prepares project (skip if exists)" → Task 1 (`ensure_base_clone` fetch-if-present). ✅
- "Create worktree/branch per run" → Task 1 (`ensure_worktree`). ✅
- "Run configurable agent command to generate a plan, commit it" → Task 2 (`generate_plan`). ✅
- "Return a git reference; body never crosses contract" → Task 3 (`artifactRefs` serialization) + Task 4 (no-leak test). ✅
- "Idempotency from git/fs, no DB; retry-safe across restart" → Task 2 (short-circuit), Task 3 (`test_fresh_instance_reuses_git_state_across_restart`). ✅
- "Reuse async submit→poll + Gateway sticky routing" → Task 3/4 (transport unchanged); Task 6 runs it through the Gateway. ✅
- "Remove ledger, migrations, psycopg, `WORKER_DATABASE_URL`" → Task 4. ✅
- "Remove ledger Postgres service; delete obsolete SQL" → Task 5. ✅
- "Temporal PLANNING adapter maps ref into `implementationPlanRef`" → already satisfied by existing `status.artifactRefs[0] ?? status.executionId` mapping in `gateway-engine-activities.ts`; no code change needed, exercised in Task 6. ✅
- "Credential resolution via `credentialRef` reference" → the slice uses credential-free remotes (Task 6 `git://`); real credential resolution is deferred, consistent with the spec's out-of-scope note. ✅

**2. Placeholder scan:** No "TBD"/"handle errors"/"similar to Task N". Every code and command step is concrete. ✅

**3. Type consistency:** `WorkerConfig(workspace_root, agent_command, agent_timeout_seconds)` identical across Tasks 3, 4, 6. `generate_plan(worktree, workflow_run_id, agent_command, timeout_seconds)` identical in Tasks 2 and 3. `plan_ref` returns `{"branch","commitSha","planPath"}` in Task 1 and is serialized as `"{branch}@{commitSha}:{planPath}"` in Task 3 and asserted in the same form in Tasks 3/4. `ensure_worktree(base, workspace_root, workflow_run_id, base_branch, requested_commit)` identical in Tasks 1 and 3. Endpoint set and `ExecutionStatus.artifactRefs` match `models.py`. ✅
