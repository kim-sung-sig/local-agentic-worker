package com.example.worker.scm.application;

public interface SourceControlPlugin {

    /** Rejects the request if {@code command.qaPassed()} is false. Idempotent by {@code idempotencyKey()}. */
    PullRequestResult createDraftPullRequest(CreateDraftPullRequestCommand command);

    /** Returns {@code null} if no pull request exists for the branch — a normal, non-exceptional outcome. */
    PullRequestResult getPullRequest(String workspacePath, String branchName);

    /** Requires an existing (draft) pull request. Idempotent by {@code idempotencyKey()}. */
    PullRequestResult mergePullRequest(MergePullRequestCommand command);

    record CreateDraftPullRequestCommand(
            String idempotencyKey,
            String workspacePath,
            String baseBranch,
            String branchName,
            String title,
            String body,
            boolean qaPassed
    ) {
    }

    record MergePullRequestCommand(
            String idempotencyKey,
            String workspacePath,
            String branchName
    ) {
    }

    record PullRequestResult(String url, String status) {
    }
}
