import subprocess
from pathlib import Path

import pytest

from agent_worker import workspace


def _run(args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


@pytest.fixture
def remote(tmp_path):
    bare = tmp_path / "remote.git"
    _run(["git", "init", "--bare", "-b", "main", str(bare)])
    seed = tmp_path / "seed"
    _run(["git", "clone", str(bare), str(seed)])
    (seed / "README.md").write_text("seed\n", encoding="utf-8")
    _run(["git", "-C", str(seed), "add", "-A"])
    _run(["git", "-C", str(seed), "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "seed"])
    _run(["git", "-C", str(seed), "push", "origin", "main"])
    return bare.as_uri()  # file:// URI usable directly by git clone in tests


def test_ensure_base_clone_creates_then_reuses(tmp_path, remote):
    root = tmp_path / "ws"
    base = workspace.ensure_base_clone(root, remote)
    assert (base / ".git").exists()
    # Second call must not fail and must reuse the same directory (fetch path).
    base_again = workspace.ensure_base_clone(root, remote)
    assert base_again == base


def test_ensure_worktree_creates_branch_then_reuses(tmp_path, remote):
    root = tmp_path / "ws"
    base = workspace.ensure_base_clone(root, remote)
    worktree = workspace.ensure_worktree(base, root, "run-1", "main", None)
    assert (worktree / ".git").exists()
    branch = subprocess.run(
        ["git", "-C", str(worktree), "rev-parse", "--abbrev-ref", "HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
    assert branch == "agent/plan/run-1"
    assert workspace.ensure_worktree(base, root, "run-1", "main", None) == worktree
    assert workspace.plan_ref(worktree, "run-1") is None  # no plan committed yet
