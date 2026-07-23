import re
from typing import Literal
from urllib.parse import urlparse

from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictInt, StrictStr, model_validator


CONTRACT_VERSION = "agent-worker/v1"
_FORBIDDEN_KEY = re.compile(r"^(token|password|secret|apiKey)$", re.IGNORECASE)
_ABSOLUTE_PATH = re.compile(r"^(?:[A-Za-z]:[\\/]|/|file://)", re.IGNORECASE)


def _unsafe(value: object) -> bool:
    if isinstance(value, str):
        return bool(_ABSOLUTE_PATH.search(value))
    if isinstance(value, dict):
        return any(_FORBIDDEN_KEY.search(key) or _unsafe(nested) for key, nested in value.items())
    if isinstance(value, list):
        return any(_unsafe(nested) for nested in value)
    return False


class ProjectExecutionSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")

    projectId: StrictStr = Field(min_length=1)
    repositoryUri: StrictStr = Field(min_length=1)
    baseBranch: StrictStr = Field(min_length=1)
    credentialRef: StrictStr | None = Field(default=None, min_length=1)
    requestedSourceCommit: StrictStr | None = Field(default=None, min_length=1)

    @model_validator(mode="after")
    def remote_repository(self):
        uri = urlparse(self.repositoryUri)
        if uri.scheme == "file" or not uri.scheme or not uri.netloc or uri.username or uri.password or uri.query or uri.fragment:
            raise ValueError("repositoryUri must be a credential-free remote repository URI")
        return self


class ExecutionSubmission(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal[CONTRACT_VERSION]
    idempotencyKey: StrictStr = Field(min_length=1)
    workflowRunId: StrictStr = Field(min_length=1)
    stage: Literal["INTAKE", "PLANNING", "IMPLEMENTATION", "QA"]
    attemptNumber: StrictInt = Field(gt=0)
    stageExecutionGeneration: StrictInt = Field(gt=0)
    adapterId: StrictStr = Field(min_length=1)
    project: ProjectExecutionSnapshot
    mode: Literal["READ", "WRITE"]

    @model_validator(mode="after")
    def safe_and_idempotent(self):
        expected = f"{self.workflowRunId}:{self.stage}:{self.attemptNumber}:{self.stageExecutionGeneration}"
        if self.idempotencyKey != expected:
            raise ValueError("idempotencyKey must match workflowRunId:stage:attemptNumber:stageExecutionGeneration")
        if _unsafe(self.model_dump()):
            raise ValueError("submission contains a local path or secret-like key")
        return self


class ExecutionResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    executionId: StrictStr = Field(min_length=1)


class ExecutionStatus(BaseModel):
    model_config = ConfigDict(extra="forbid")

    executionId: StrictStr = Field(min_length=1)
    status: Literal["ACCEPTED", "RUNNING", "COMPLETED", "FAILED", "CANCELLED"]
    terminal: StrictBool
    artifactRefs: list[StrictStr] = Field(default_factory=list)


class ExecutionEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")

    executionId: StrictStr = Field(min_length=1)
    cursor: StrictInt = Field(gt=0)
    type: Literal["accepted", "running", "completed", "failed", "cancelled"]
    data: dict[StrictStr, object] = Field(default_factory=dict)


class WorkerCapabilities(BaseModel):
    model_config = ConfigDict(extra="forbid")

    workerId: StrictStr = Field(min_length=1)
    adapterIds: list[StrictStr]
    modes: list[Literal["READ", "WRITE"]]


# Backwards-compatible local imports; the public HTTP boundary uses the contract names above.
Project = ProjectExecutionSnapshot
Submission = ExecutionSubmission
