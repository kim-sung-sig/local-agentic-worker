package com.example.worker.runtime.application;

public interface WorkspaceRuntime {

    /**
     * Idempotent — a repeated call for the same {@code runId} returns the existing
     * {@link Workspace} without re-running {@code git worktree add}.
     */
    Workspace acquire(String runId, String branchName, String baseBranch);

    /** Removes the worktree for {@code runId}. Callers must only invoke this at a terminal run state. */
    void cleanup(String runId);

    record Workspace(String runId, String path, String branchName) {
    }
}
