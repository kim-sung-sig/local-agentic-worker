import re
import threading
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

from . import planner, workspace
from .models import ExecutionSubmission, _ABSOLUTE_PATH

_TERMINAL = {"COMPLETED", "FAILED", "CANCELLED"}

# Unanchored variant of models._ABSOLUTE_PATH (which is anchored with `^` to
# validate whole-field values) so failure-path diagnostics can be scrubbed of
# absolute host paths / file:// URIs wherever they appear in free-form text,
# e.g. inside a subprocess's stderr embedded in an exception message.
_ABSOLUTE_PATH_ANYWHERE = re.compile(_ABSOLUTE_PATH.pattern.lstrip("^") + r'[^\s"\']*', re.IGNORECASE)
_REDACTED = "[REDACTED]"


def _sanitize_error(text: str) -> str:
    """Redact absolute host paths / file:// URIs from an exception message.

    Keeps a useful diagnostic (the rest of the message) so failures stay
    debuggable without leaking local filesystem layout across the contract.
    """
    return _ABSOLUTE_PATH_ANYWHERE.sub(_REDACTED, text)


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
            error = f"{type(exc).__name__}: {_sanitize_error(str(exc))}"
            self._append(execution_id, "failed", "FAILED", data={"error": error})

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
