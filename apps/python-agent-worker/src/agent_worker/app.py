import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query

from .ledger import Ledger
from .models import ExecutionEvent, ExecutionResult, ExecutionStatus, ExecutionSubmission, WorkerCapabilities


def create_app(database_url: str | None = None) -> FastAPI:
    ledger: Ledger | None = None

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        nonlocal ledger
        url = database_url or os.environ.get("WORKER_DATABASE_URL")
        if not url:
            raise RuntimeError("WORKER_DATABASE_URL is required")
        ledger = Ledger(url)
        try:
            yield
        finally:
            ledger.close()

    def current_ledger() -> Ledger:
        if ledger is None:
            raise RuntimeError("worker ledger is unavailable")
        return ledger

    app = FastAPI(lifespan=lifespan)

    @app.post("/v1/executions", response_model=ExecutionResult)
    def submit(submission: ExecutionSubmission):
        return {"executionId": current_ledger().submit(submission)}

    @app.get("/v1/executions/{execution_id}", response_model=ExecutionStatus)
    def execution(execution_id: str):
        result = current_ledger().status(execution_id)
        if not result:
            raise HTTPException(404, "execution not found")
        return result

    @app.get("/v1/executions/{execution_id}/events", response_model=list[ExecutionEvent])
    def events(execution_id: str, after: int = Query(0, ge=0)):
        if not current_ledger().status(execution_id):
            raise HTTPException(404, "execution not found")
        return current_ledger().events(execution_id, after)

    @app.post("/v1/executions/{execution_id}:cancel")
    def cancel(execution_id: str):
        if not current_ledger().status(execution_id):
            raise HTTPException(404, "execution not found")
        raise HTTPException(409, "cancellation unsupported for synchronous fake executions")

    @app.get("/v1/capabilities", response_model=WorkerCapabilities)
    def capabilities():
        return {"workerId": "python-agent-worker", "adapterIds": ["fake-agent"], "modes": ["READ", "WRITE"]}

    return app


app = create_app()
