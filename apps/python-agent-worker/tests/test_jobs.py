import subprocess
import sys
import time
from pathlib import Path

import pytest

from agent_worker.jobs import PlanningJobs, WorkerConfig
from agent_worker.models import ExecutionSubmission, ProjectExecutionSnapshot


def _run(args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


def _submission(run_id: str, remote_uri: str) -> ExecutionSubmission:
    # NOTE: built via model_construct (bypasses validation) rather than model_validate.
    # The `remote` fixture below yields a `file://` bare-repo URI so the test can exercise
    # real, hermetic git operations end-to-end. ExecutionSubmission's validator intentionally
    # rejects file:// / local-path-like repositoryUri values (see models.py) as a production
    # safety constraint that must not be weakened; that constraint is already covered by
    # test_api.py's contract-matrix tests. Task 3 exercises the job registry's git/idempotency
    # plumbing, not input validation, so we bypass it here rather than touch models.py.
    project = ProjectExecutionSnapshot.model_construct(
        projectId="p1", repositoryUri=remote_uri, baseBranch="main",
        credentialRef=None, requestedSourceCommit=None,
    )
    return ExecutionSubmission.model_construct(
        contractVersion="agent-worker/v1",
        idempotencyKey=f"{run_id}:PLANNING:1:1",
        workflowRunId=run_id,
        stage="PLANNING",
        attemptNumber=1,
        stageExecutionGeneration=1,
        adapterId="fake-agent",
        project=project,
        mode="READ",
    )


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
