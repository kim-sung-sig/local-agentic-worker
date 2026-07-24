import subprocess
import sys
from pathlib import Path

import pytest

from agent_worker import planner, workspace


def _run(args, cwd=None):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True)


@pytest.fixture
def worktree(tmp_path):
    bare = tmp_path / "remote.git"
    _run(["git", "init", "--bare", "-b", "main", str(bare)])
    seed = tmp_path / "seed"
    _run(["git", "clone", str(bare), str(seed)])
    (seed / "README.md").write_text("seed\n", encoding="utf-8")
    _run(["git", "-C", str(seed), "add", "-A"])
    _run(["git", "-C", str(seed), "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-m", "seed"])
    _run(["git", "-C", str(seed), "push", "origin", "main"])
    root = tmp_path / "ws"
    base = workspace.ensure_base_clone(root, bare.as_uri())
    return workspace.ensure_worktree(base, root, "run-1", "main", None)


@pytest.fixture
def agent_command(tmp_path):
    # A portable fake agent: writes the plan file at $PLAN_PATH and bumps a counter file.
    counter = tmp_path / "invocations.txt"
    script = tmp_path / "fake_agent.py"
    script.write_text(
        "import os, pathlib\n"
        f"c = pathlib.Path(r'{counter}')\n"
        "c.write_text(str(int(c.read_text()) + 1) if c.exists() else '1')\n"
        "p = pathlib.Path(os.environ['PLAN_PATH'])\n"
        "p.parent.mkdir(parents=True, exist_ok=True)\n"
        "p.write_text('# Plan for ' + os.environ['WORKFLOW_RUN_ID'] + '\\n')\n",
        encoding="utf-8",
    )
    command = f'"{sys.executable}" "{script}"'
    return command, counter


def test_generate_plan_runs_agent_commits_and_returns_ref(worktree, agent_command):
    command, counter = agent_command
    ref = planner.generate_plan(worktree, "run-1", command, timeout_seconds=30)
    assert ref["branch"] == "agent/plan/run-1"
    assert ref["planPath"] == "docs/plans/run-1.plan.md"
    assert len(ref["commitSha"]) == 40
    assert counter.read_text() == "1"
    # Idempotent: second call must NOT re-run the agent and must return the same commit.
    ref_again = planner.generate_plan(worktree, "run-1", command, timeout_seconds=30)
    assert ref_again == ref
    assert counter.read_text() == "1"


def test_generate_plan_fails_when_agent_produces_no_plan(worktree):
    noop = f'"{sys.executable}" -c "pass"'
    with pytest.raises(RuntimeError, match="did not produce"):
        planner.generate_plan(worktree, "run-1", noop, timeout_seconds=30)
