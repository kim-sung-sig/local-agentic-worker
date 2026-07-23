from fastapi.testclient import TestClient

from agent_worker.app import create_app


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


def test_duplicate_submission_reuses_execution_and_events_after_reopen(tmp_path):
    state_path = tmp_path / "worker.sqlite3"
    with TestClient(create_app(state_path)) as client:
        first = client.post("/v1/executions", json=payload("run-1:QA:1:1"))
        second = client.post("/v1/executions", json=payload("run-1:QA:1:1"))
        assert first.status_code == second.status_code == 200
        assert first.json()["executionId"] == second.json()["executionId"]
        execution_id = first.json()["executionId"]
        events = client.get(f"/v1/executions/{execution_id}/events").json()
        assert [(event["cursor"], event["type"]) for event in events] == [
            (1, "accepted"), (2, "running"), (3, "completed")
        ]

    with TestClient(create_app(state_path)) as reopened:
        duplicate = reopened.post("/v1/executions", json=payload("run-1:QA:1:1"))
        assert duplicate.json()["executionId"] == execution_id
        assert reopened.get(f"/v1/executions/{execution_id}/events?after=2").json() == [
            {"executionId": execution_id, "cursor": 3, "type": "completed", "data": {}}
        ]


def test_execution_status_cancel_capabilities_and_unsafe_input(tmp_path):
    with TestClient(create_app(tmp_path / "worker.sqlite3")) as client:
        created = client.post("/v1/executions", json=payload("run-2:QA:1:1")).json()
        execution_id = created["executionId"]
        status = client.get(f"/v1/executions/{execution_id}")
        assert status.json() == {
            "executionId": execution_id,
            "status": "COMPLETED",
            "terminal": True,
            "artifactRefs": [],
        }
        assert client.post(f"/v1/executions/{execution_id}:cancel").status_code == 200
        assert client.get("/v1/capabilities").json() == {
            "workerId": "python-agent-worker",
            "adapterIds": ["fake-agent"],
            "modes": ["READ", "WRITE"],
        }
        unsafe = payload("run-3:QA:1:1")
        unsafe["workspaceRef"] = "C:\\\\secret"
        assert client.post("/v1/executions", json=unsafe).status_code == 422
