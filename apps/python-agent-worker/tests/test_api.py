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
