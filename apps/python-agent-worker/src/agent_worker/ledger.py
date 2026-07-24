from pathlib import Path
import uuid

import psycopg
from psycopg.types.json import Jsonb

from .models import ExecutionSubmission


class Ledger:
    def __init__(self, database_url: str):
        self.connection = psycopg.connect(database_url, autocommit=True)
        migration = Path(__file__).parents[2] / "migrations" / "0001_agent_worker_ledger.sql"
        with self.connection.transaction():
            self.connection.execute(migration.read_text(encoding="utf-8"))

    def submit(self, submission: ExecutionSubmission) -> str:
        execution_id = str(uuid.uuid4())
        with self.connection.transaction():
            inserted = self.connection.execute(
                "INSERT INTO agent_worker.executions (execution_id, idempotency_key, status, artifact_refs) VALUES (%s, %s, 'COMPLETED', %s) ON CONFLICT (idempotency_key) DO NOTHING RETURNING execution_id",
                (execution_id, submission.idempotencyKey, Jsonb([])),
            ).fetchone()
            if inserted:
                with self.connection.cursor() as cursor:
                    cursor.executemany(
                        "INSERT INTO agent_worker.execution_events (execution_id, cursor, type, data) VALUES (%s, %s, %s, %s)",
                        [(execution_id, 1, "accepted", Jsonb({})), (execution_id, 2, "running", Jsonb({})), (execution_id, 3, "completed", Jsonb({}))],
                    )
                return str(inserted[0])
            return str(self.connection.execute(
                "SELECT execution_id FROM agent_worker.executions WHERE idempotency_key = %s", (submission.idempotencyKey,)
            ).fetchone()[0])

    def status(self, execution_id: str):
        row = self.connection.execute(
            "SELECT status, artifact_refs FROM agent_worker.executions WHERE execution_id = %s", (execution_id,)
        ).fetchone()
        if not row:
            return None
        return {"executionId": execution_id, "status": row[0], "terminal": row[0] in {"COMPLETED", "FAILED", "CANCELLED"}, "artifactRefs": row[1]}

    def events(self, execution_id: str, after: int):
        rows = self.connection.execute(
            "SELECT cursor, type, data FROM agent_worker.execution_events WHERE execution_id = %s AND cursor > %s ORDER BY cursor",
            (execution_id, after),
        ).fetchall()
        return [{"executionId": execution_id, "cursor": cursor, "type": type_, "data": data} for cursor, type_, data in rows]

    def close(self):
        self.connection.close()
