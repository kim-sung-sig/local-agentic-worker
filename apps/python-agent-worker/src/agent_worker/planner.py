import os
import subprocess
from pathlib import Path

from .workspace import PLAN_RELPATH_TEMPLATE, plan_ref


def generate_plan(worktree: Path, workflow_run_id: str, agent_command: str, timeout_seconds: int) -> dict:
    existing = plan_ref(worktree, workflow_run_id)
    if existing is not None:
        return existing

    plan_relpath = PLAN_RELPATH_TEMPLATE.format(workflow_run_id=workflow_run_id)
    plan_file = Path(worktree) / plan_relpath
    plan_file.parent.mkdir(parents=True, exist_ok=True)

    env = {**os.environ, "WORKFLOW_RUN_ID": workflow_run_id, "PLAN_PATH": str(plan_file)}
    result = subprocess.run(agent_command, cwd=str(worktree), shell=True, env=env, capture_output=True, text=True, timeout=timeout_seconds)
    if result.returncode != 0:
        raise RuntimeError(f"agent command failed: {result.stderr.strip()}")
    if not plan_file.exists():
        raise RuntimeError("agent command did not produce the plan file")

    _commit(worktree, plan_relpath, f"plan: {workflow_run_id}")
    ref = plan_ref(worktree, workflow_run_id)
    if ref is None:
        raise RuntimeError("plan commit was not created")
    return ref


def _commit(worktree: Path, plan_relpath: str, message: str) -> None:
    add = subprocess.run(["git", "add", plan_relpath], cwd=str(worktree), capture_output=True, text=True)
    if add.returncode != 0:
        raise RuntimeError(f"git add failed: {add.stderr.strip()}")
    commit = subprocess.run(
        ["git", "-c", "user.email=agent@worker.local", "-c", "user.name=agent-worker", "commit", "-m", message],
        cwd=str(worktree), capture_output=True, text=True,
    )
    if commit.returncode != 0:
        raise RuntimeError(f"git commit failed: {commit.stderr.strip()}")
