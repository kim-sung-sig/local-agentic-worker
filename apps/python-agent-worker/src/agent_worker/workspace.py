import hashlib
import subprocess
from pathlib import Path

PLAN_RELPATH_TEMPLATE = "docs/plans/{workflow_run_id}.plan.md"


def repo_key(repository_uri: str) -> str:
    return hashlib.sha256(repository_uri.encode("utf-8")).hexdigest()[:16]


def branch_name(workflow_run_id: str) -> str:
    return f"agent/plan/{workflow_run_id}"


def _git(args: list[str], cwd: Path) -> str:
    result = subprocess.run(["git", *args], cwd=str(cwd), capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def ensure_base_clone(workspace_root: Path, repository_uri: str) -> Path:
    base = Path(workspace_root) / "base" / repo_key(repository_uri)
    if (base / ".git").exists():
        _git(["fetch", "--all", "--prune"], cwd=base)
        return base
    base.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(["git", "clone", repository_uri, str(base)], capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"git clone failed: {result.stderr.strip()}")
    return base


def ensure_worktree(base: Path, workspace_root: Path, workflow_run_id: str, base_branch: str, requested_commit: str | None) -> Path:
    worktree = Path(workspace_root) / "worktrees" / workflow_run_id
    if (worktree / ".git").exists():
        return worktree
    worktree.parent.mkdir(parents=True, exist_ok=True)
    start_point = requested_commit or f"origin/{base_branch}"
    _git(["worktree", "add", "-b", branch_name(workflow_run_id), str(worktree), start_point], cwd=base)
    return worktree


def plan_ref(worktree: Path, workflow_run_id: str) -> dict | None:
    plan_path = PLAN_RELPATH_TEMPLATE.format(workflow_run_id=workflow_run_id)
    probe = subprocess.run(["git", "cat-file", "-e", f"HEAD:{plan_path}"], cwd=str(worktree), capture_output=True)
    if probe.returncode != 0:
        return None
    return {"branch": branch_name(workflow_run_id), "commitSha": _git(["rev-parse", "HEAD"], cwd=worktree), "planPath": plan_path}
