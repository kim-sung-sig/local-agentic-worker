from typing import Literal
from urllib.parse import urlparse

from pydantic import BaseModel, ConfigDict, Field, model_validator


CONTRACT_VERSION = "agent-worker/v1"
_FORBIDDEN_KEYS = {"token", "password", "secret", "apikey", "workspaceref"}


def _unsafe(value: object, key: str = "") -> bool:
    if key.lower() in _FORBIDDEN_KEYS:
        return True
    if isinstance(value, str):
        return value.startswith(("/", "\\", "file://")) or (len(value) > 2 and value[1] == ":" and value[2] in "\\\\/")
    if isinstance(value, dict):
        return any(_unsafe(nested, nested_key) for nested_key, nested in value.items())
    if isinstance(value, list):
        return any(_unsafe(nested) for nested in value)
    return False


class Project(BaseModel):
    model_config = ConfigDict(extra="forbid")

    projectId: str = Field(min_length=1)
    repositoryUri: str = Field(min_length=1)
    baseBranch: str = Field(min_length=1)
    credentialRef: str | None = Field(min_length=1)
    requestedSourceCommit: str | None = Field(min_length=1)

    @model_validator(mode="after")
    def remote_repository(self):
        uri = urlparse(self.repositoryUri)
        if uri.scheme not in {"http", "https", "ssh", "git"} or not uri.netloc or uri.username or uri.password or uri.query or uri.fragment:
            raise ValueError("repositoryUri must be a credential-free remote repository URI")
        return self


class Submission(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal[CONTRACT_VERSION]
    idempotencyKey: str = Field(min_length=1)
    workflowRunId: str = Field(min_length=1)
    stage: Literal["INTAKE", "PLANNING", "IMPLEMENTATION", "QA"]
    attemptNumber: int = Field(gt=0)
    stageExecutionGeneration: int = Field(gt=0)
    adapterId: str = Field(min_length=1)
    project: Project
    mode: Literal["READ", "WRITE"]

    @model_validator(mode="after")
    def safe_and_idempotent(self):
        expected = f"{self.workflowRunId}:{self.stage}:{self.attemptNumber}:{self.stageExecutionGeneration}"
        if self.idempotencyKey != expected:
            raise ValueError("idempotencyKey must match workflowRunId:stage:attemptNumber:stageExecutionGeneration")
        if _unsafe(self.model_dump()):
            raise ValueError("submission contains a local path or secret-like key")
        return self
