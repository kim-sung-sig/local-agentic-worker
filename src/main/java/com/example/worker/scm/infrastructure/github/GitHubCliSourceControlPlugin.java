package com.example.worker.scm.infrastructure.github;

import com.example.worker.scm.application.SourceControlPlugin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class GitHubCliSourceControlPlugin implements SourceControlPlugin {

    private static final Logger log = LoggerFactory.getLogger(GitHubCliSourceControlPlugin.class);

    private final CommandExecutor commandExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, PullRequestResult> resultsByIdempotencyKey = new ConcurrentHashMap<>();

    public GitHubCliSourceControlPlugin() {
        this(GitHubCliSourceControlPlugin::executeProcess);
    }

    public GitHubCliSourceControlPlugin(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public synchronized PullRequestResult createDraftPullRequest(CreateDraftPullRequestCommand command) {
        if (!command.qaPassed()) {
            throw new IllegalStateException(
                    "Cannot create a draft PR without a passed QA attempt for " + command.branchName());
        }

        PullRequestResult cached = resultsByIdempotencyKey.get(command.idempotencyKey());
        if (cached != null) {
            return cached;
        }

        // Durable idempotency check: the in-memory cache only survives this process's
        // lifetime, but a Temporal Activity can be retried after a Worker restart wipes it.
        // Consulting GitHub's own state via `gh pr view` catches that case too.
        PullRequestResult existing = getPullRequest(command.workspacePath(), command.branchName());
        if (existing != null) {
            resultsByIdempotencyKey.put(command.idempotencyKey(), existing);
            return existing;
        }

        log.info("[SCM] Draft PR 생성: {} -> {}", command.branchName(), command.baseBranch());
        String output = commandExecutor.execute(command.workspacePath(),
                "gh", "pr", "create", "--draft",
                "--base", command.baseBranch(), "--head", command.branchName(),
                "--title", command.title(), "--body", command.body());

        PullRequestResult result = new PullRequestResult(extractUrl(output), "DRAFT");
        resultsByIdempotencyKey.put(command.idempotencyKey(), result);
        return result;
    }

    @Override
    public PullRequestResult getPullRequest(String workspacePath, String branchName) {
        String output = commandExecutor.execute(workspacePath,
                "gh", "pr", "view", branchName, "--json", "url,state");
        return parsePullRequest(output);
    }

    @Override
    public synchronized PullRequestResult mergePullRequest(MergePullRequestCommand command) {
        PullRequestResult cached = resultsByIdempotencyKey.get(command.idempotencyKey());
        if (cached != null && "MERGED".equals(cached.status())) {
            return cached;
        }

        PullRequestResult existing = getPullRequest(command.workspacePath(), command.branchName());
        if (existing == null) {
            throw new IllegalStateException(
                    "Cannot merge: no draft PR exists for branch " + command.branchName());
        }
        if ("MERGED".equals(existing.status())) {
            // Durable idempotency: already merged per GitHub's own state (e.g. a retried
            // Activity after a Worker restart) — avoid re-invoking `gh pr merge`.
            resultsByIdempotencyKey.put(command.idempotencyKey(), existing);
            return existing;
        }

        log.info("[SCM] PR 병합: {}", command.branchName());
        commandExecutor.execute(command.workspacePath(), "gh", "pr", "merge", command.branchName(), "--merge");

        PullRequestResult result = new PullRequestResult(existing.url(), "MERGED");
        resultsByIdempotencyKey.put(command.idempotencyKey(), result);
        return result;
    }

    private PullRequestResult parsePullRequest(String output) {
        try {
            JsonNode node = objectMapper.readTree(output);
            JsonNode urlNode = node.get("url");
            JsonNode stateNode = node.get("state");
            if (urlNode == null || stateNode == null) {
                return null;
            }
            return new PullRequestResult(urlNode.asText(), stateNode.asText());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse gh pr view output: " + output, e);
        }
    }

    private static String extractUrl(String output) {
        return output.trim().lines()
                .filter(line -> line.startsWith("https://"))
                .findFirst()
                .orElse(output.trim());
    }

    private static String executeProcess(String workDir, String... cmd) {
        AtomicReference<String> outputRef = new AtomicReference<>("");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(new File(workDir))
                    .redirectErrorStream(true);

            Process process = pb.start();

            Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    outputRef.set(new String(process.getInputStream().readAllBytes()));
                } catch (IOException ignored) {
                }
            });

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.interrupt();
                throw new IllegalStateException(cmd[0] + " timed out");
            }
            reader.join();

            if (process.exitValue() != 0) {
                throw new IllegalStateException(String.join(" ", cmd) + " failed: " + outputRef.get().trim());
            }
            return outputRef.get();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(String.join(" ", cmd) + " error", e);
        }
    }

    @FunctionalInterface
    public interface CommandExecutor {
        String execute(String workDir, String... command);
    }
}
