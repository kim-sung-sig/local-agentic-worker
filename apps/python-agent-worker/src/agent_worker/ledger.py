import json
import sqlite3
import threading
import uuid
from pathlib import Path

from .models import Submission


class Ledger:
    def __init__(self, path: Path):
        self.connection = sqlite3.connect(path, check_same_thread=False)
        self.lock = threading.Lock()
        self.connection.execute("PRAGMA journal_mode=WAL")
        self.connection.executescript("""
            CREATE TABLE IF NOT EXISTS executions (
                execution_id TEXT PRIMARY KEY, idempotency_key TEXT NOT NULL UNIQUE,
                status TEXT NOT NULL, artifact_refs TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS events (
                execution_id TEXT NOT NULL, cursor INTEGER NOT NULL,
                type TEXT NOT NULL, data TEXT NOT NULL,
                PRIMARY KEY (execution_id, cursor)
            );
        """)

    def submit(self, submission: Submission) -> str:
        with self.lock:
            row = self.connection.execute("SELECT execution_id FROM executions WHERE idempotency_key = ?", (submission.idempotencyKey,)).fetchone()
            if row:
                return row[0]
            execution_id = str(uuid.uuid4())
            with self.connection:
                self.connection.execute(
                    "INSERT INTO executions VALUES (?, ?, 'COMPLETED', '[]')",
                    (execution_id, submission.idempotencyKey),
                )
                self.connection.executemany(
                    "INSERT INTO events VALUES (?, ?, ?, '{}')",
                    [(execution_id, 1, "accepted"), (execution_id, 2, "running"), (execution_id, 3, "completed")],
                )
            return execution_id

    def status(self, execution_id: str):
        with self.lock:
            row = self.connection.execute("SELECT status, artifact_refs FROM executions WHERE execution_id = ?", (execution_id,)).fetchone()
            if not row:
                return None
            return {"executionId": execution_id, "status": row[0], "terminal": row[0] in {"COMPLETED", "FAILED", "CANCELLED"}, "artifactRefs": json.loads(row[1])}

    def events(self, execution_id: str, after: int):
        with self.lock:
            rows = self.connection.execute("SELECT cursor, type, data FROM events WHERE execution_id = ? AND cursor > ? ORDER BY cursor", (execution_id, after)).fetchall()
            return [{"executionId": execution_id, "cursor": cursor, "type": type_, "data": json.loads(data)} for cursor, type_, data in rows]

    def close(self):
        with self.lock:
            self.connection.close()
