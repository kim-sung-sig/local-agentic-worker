import os
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query

from .ledger import Ledger
from .models import Submission


def create_app(state_path: Path | None = None) -> FastAPI:
    path = state_path or Path(os.environ.get("WORKER_STATE_PATH", Path(__file__).with_name("worker-state.sqlite3")))
    path.parent.mkdir(parents=True, exist_ok=True)
    ledger = Ledger(path)
    app = FastAPI()

    @app.post("/v1/executions")
    def submit(submission: Submission):
        return {"executionId": ledger.submit(submission)}

    @app.get("/v1/executions/{execution_id}")
    def execution(execution_id: str):
        result = ledger.status(execution_id)
        if not result:
            raise HTTPException(404, "execution not found")
        return result

    @app.get("/v1/executions/{execution_id}/events")
    def events(execution_id: str, after: int = Query(0, ge=0)):
        if not ledger.status(execution_id):
            raise HTTPException(404, "execution not found")
        return ledger.events(execution_id, after)

    @app.post("/v1/executions/{execution_id}:cancel")
    def cancel(execution_id: str):
        result = ledger.cancel(execution_id)
        if not result:
            raise HTTPException(404, "execution not found")
        return result

    @app.get("/v1/capabilities")
    def capabilities():
        return {"workerId": "python-agent-worker", "adapterIds": ["fake-agent"], "modes": ["READ", "WRITE"]}

    return app


app = create_app()
