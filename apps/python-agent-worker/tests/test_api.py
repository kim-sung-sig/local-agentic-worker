import os
import socket
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from agent_worker.app import create_app
from agent_worker.ledger import Ledger
from agent_worker.models import Submission, WorkerCapabilities


def payload(key: str) -> dict:
    workflow_run_id, stage, attempt_number, generation = key.split(":")
    return {
        "contractVersion": "agent-worker/v1",
        "idempotencyKey": key,
        "workflowRunId": workflow_run_id,
        "stage": stage,
        "attemptNumber": int(attempt_number),
        "stageExecutionGeneration": int(generation),
        "adapterId": "fake-agent",
        "project": {
            "projectId": "project-1",
            "repositoryUri": "https://github.com/example/repository.git",
            "baseBranch": "main",
            "credentialRef": None,
            "requestedSourceCommit": None,
        },
        "mode": "READ",
    }


def _free_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


@pytest.fixture(scope="session")
def postgres_url():
    if subprocess.run(["docker", "info"], capture_output=True).returncode:
        pytest.skip("Docker is unavailable; PostgreSQL integration tests require docker info")
    port = _free_port()
    name = f"agent-worker-ledger-{port}"
    subprocess.run(
        ["docker", "run", "--rm", "-d", "--name", name, "-e", "POSTGRES_PASSWORD=ledger", "-e", "POSTGRES_DB=ledger", "-p", f"{port}:5432", "postgres:16-alpine"],
        check=True,
    )
    url = f"postgresql://postgres:ledger@127.0.0.1:{port}/ledger"
    try:
        import psycopg

        for _ in range(30):
            try:
                with psycopg.connect(url) as connection:
                    connection.execute("SELECT 1")
                break
            except psycopg.OperationalError:
                time.sleep(1)
        else:
            raise RuntimeError("PostgreSQL container did not become ready")
        yield url
    finally:
        subprocess.run(["docker", "rm", "-f", name], check=False)


def test_postgres_ledger_is_durable_across_app_restart(postgres_url, tmp_path):
    with TestClient(create_app(postgres_url)) as first:
        created = first.post("/v1/executions", json=payload("run-1:QA:1:1"))
        duplicate = first.post("/v1/executions", json=payload("run-1:QA:1:1"))
        assert created.status_code == duplicate.status_code == 200
        assert created.json() == duplicate.json()
        execution_id = created.json()["executionId"]
        assert first.get(f"/v1/executions/{execution_id}").json() == {
            "executionId": execution_id, "status": "COMPLETED", "terminal": True, "artifactRefs": []
        }
        assert first.get(f"/v1/executions/{execution_id}/events").json() == [
            {"executionId": execution_id, "cursor": 1, "type": "accepted", "data": {}},
            {"executionId": execution_id, "cursor": 2, "type": "running", "data": {}},
            {"executionId": execution_id, "cursor": 3, "type": "completed", "data": {}},
        ]
        assert first.get("/v1/capabilities").json() == {
            "workerId": "python-agent-worker", "adapterIds": ["fake-agent"], "modes": ["READ", "WRITE"]
        }
        assert first.post(f"/v1/executions/{execution_id}:cancel").status_code == 409

    with TestClient(create_app(postgres_url)) as reopened:
        assert reopened.post("/v1/executions", json=payload("run-1:QA:1:1")).json() == {"executionId": execution_id}
        assert reopened.get(f"/v1/executions/{execution_id}/events?after=2").json() == [
            {"executionId": execution_id, "cursor": 3, "type": "completed", "data": {}},
        ]
    assert not list(Path(tmp_path).glob("*.sqlite*"))


def test_postgres_schema_and_concurrent_idempotency(postgres_url):
    import psycopg

    ledger_one = Ledger(postgres_url)
    ledger_two = Ledger(postgres_url)
    submission = Submission.model_validate(payload("run-concurrent:QA:1:1"))
    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            execution_ids = list(executor.map(lambda ledger: ledger.submit(submission), (ledger_one, ledger_two)))
        assert len(set(execution_ids)) == 1
        with psycopg.connect(postgres_url) as connection:
            tables = connection.execute(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'agent_worker' ORDER BY table_name"
            ).fetchall()
            assert tables == [("execution_events",), ("executions",)]
            assert connection.execute(
                "SELECT count(*) FROM agent_worker.executions WHERE idempotency_key = %s", (submission.idempotencyKey,)
            ).fetchone() == (1,)
            assert connection.execute(
                "SELECT count(*) FROM agent_worker.execution_events WHERE execution_id = %s", (execution_ids[0],)
            ).fetchone() == (3,)
    finally:
        ledger_one.close()
        ledger_two.close()


def test_ledger_write_after_read_is_durable_across_connections(postgres_url):
    import psycopg

    ledger = Ledger(postgres_url)
    submission_a = Submission.model_validate(payload("run-durability-a:QA:1:1"))
    submission_b = Submission.model_validate(payload("run-durability-b:QA:1:1"))
    try:
        execution_id_a = ledger.submit(submission_a)
        # Bare reads under autocommit=False open an implicit transaction that
        # is never committed; this reproduces that lingering-transaction state.
        ledger.status(execution_id_a)
        ledger.events(execution_id_a, 0)

        execution_id_b = ledger.submit(submission_b)

        with psycopg.connect(postgres_url) as other_connection:
            for execution_id in (execution_id_a, execution_id_b):
                rows = other_connection.execute(
                    "SELECT type FROM agent_worker.execution_events WHERE execution_id = %s ORDER BY cursor",
                    (execution_id,),
                ).fetchall()
                assert [row[0] for row in rows] == ["accepted", "running", "completed"]
    finally:
        ledger.close()


def test_missing_database_url_fails_on_startup(monkeypatch):
    monkeypatch.delenv("WORKER_DATABASE_URL", raising=False)
    with pytest.raises(RuntimeError, match="WORKER_DATABASE_URL"):
        with TestClient(create_app()):
            pass


def test_submission_requires_nullable_snapshot_fields(postgres_url):
    with TestClient(create_app(postgres_url)) as client:
        for field in ("credentialRef", "requestedSourceCommit"):
            invalid = payload("run-required:QA:1:1")
            del invalid["project"][field]
            assert client.post("/v1/executions", json=invalid).status_code == 422


def test_submission_rejects_invalid_repository_port_at_http_boundary(postgres_url):
    invalid = payload("run-invalid-port:QA:1:1")
    invalid["project"]["repositoryUri"] = "https://github.com:invalid/repository.git"
    with TestClient(create_app(postgres_url)) as client:
        assert client.post("/v1/executions", json=invalid).status_code == 422


def test_submission_rejects_windows_root_paths_at_http_boundary(postgres_url):
    with TestClient(create_app(postgres_url)) as client:
        for base_branch in (r"\\server\share", r"\Windows\secret"):
            invalid = payload("run-windows-path:QA:1:1")
            invalid["project"]["baseBranch"] = base_branch
            assert client.post("/v1/executions", json=invalid).status_code == 422


def test_worker_capabilities_reject_empty_adapter_id():
    with pytest.raises(Exception):
        WorkerCapabilities.model_validate({"workerId": "worker", "adapterIds": [""], "modes": ["READ"]})


@pytest.mark.parametrize(
    "mutate",
    [
        lambda value: value["project"].update(repositoryUri="file:///tmp/repository"),
        lambda value: value["project"].update(repositoryUri="https://user:password@github.com/example/repository.git"),
        lambda value: value["project"].update(repositoryUri="https://github.com/example/repository.git?token=x"),
        lambda value: value["project"].update(repositoryUri="https://github.com/example/repository.git#secret"),
        lambda value: value["project"].update(repositoryUri="https://github.com:invalid/repository.git"),
        lambda value: value.update(adapterId=""),
        lambda value: value.update(attemptNumber="1"),
        lambda value: value.update(attemptNumber=1.0),
        lambda value: value.update(attemptNumber=0),
        lambda value: value.update(attemptNumber=-1),
        lambda value: value.update(stage="UNKNOWN"),
        lambda value: value.update(extra="value"),
        lambda value: value["project"].update(token="value"),
        lambda value: value["project"].update(password="value"),
        lambda value: value["project"].update(secret="value"),
        lambda value: value["project"].update(apiKey="value"),
        lambda value: value["project"].update(baseBranch="/absolute/path"),
        lambda value: value["project"].update(baseBranch="file://local"),
        lambda value: value["project"].update(baseBranch=r"C:\\local"),
        lambda value: value["project"].update(baseBranch=r"\\server\share"),
        lambda value: value["project"].update(baseBranch=r"\Windows\secret"),
        lambda value: value.update(idempotencyKey="not-the-formula"),
    ],
)
def test_submission_contract_matrix_rejects_invalid_values(mutate):
    invalid = payload("run-1:QA:1:1")
    mutate(invalid)
    with pytest.raises(Exception):
        Submission.model_validate(invalid)


@pytest.mark.parametrize("uri", ["https://github.com/example/repository.git", "ssh://github.com/example/repository.git"])
def test_submission_contract_accepts_remote_uris(uri):
    valid = payload("run-1:QA:1:1")
    valid["project"]["repositoryUri"] = uri
    assert Submission.model_validate(valid).project.repositoryUri == uri
