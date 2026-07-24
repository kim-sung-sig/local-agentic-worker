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
