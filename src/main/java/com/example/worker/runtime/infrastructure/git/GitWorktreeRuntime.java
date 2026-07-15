package com.example.worker.runtime.infrastructure.git;

import com.example.worker.runtime.application.WorkspaceRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class GitWorktreeRuntime implements WorkspaceRuntime {

    private static final Logger log = LoggerFactory.getLogger(GitWorktreeRuntime.class);

    private final Path sourceRepo;
    private final Path runtimeRoot;
    private final Map<String, Workspace> acquired = new ConcurrentHashMap<>();

    public GitWorktreeRuntime(String sourceRepoPath, String runtimeRootPath) {
        this.sourceRepo = Path.of(sourceRepoPath).toAbsolutePath().normalize();
        this.runtimeRoot = Path.of(runtimeRootPath).toAbsolutePath().normalize();
    }

    @Override
    public synchronized Workspace acquire(String runId, String branchName, String baseBranch) {
        Workspace existing = acquired.get(runId);
        if (existing != null) {
            if (!existing.branchName().equals(branchName)) {
                throw new IllegalStateException(
                        "Workspace for run " + runId + " is already bound to branch "
                                + existing.branchName() + ", requested " + branchName);
            }
            return existing;
        }

        Path path = resolveWorkspacePath(runId);

        if (Files.isDirectory(path)) {
            String currentBranch = run(path, "git", "branch", "--show-current").trim();
            if (!currentBranch.equals(branchName)) {
                throw new IllegalStateException(
                        "Existing worktree at " + path + " is on branch " + currentBranch
                                + ", expected " + branchName);
            }
        } else {
            log.info("[Workspace] worktree 생성: runId={}, branch={}, base={}", runId, branchName, baseBranch);
            run(sourceRepo, "git", "worktree", "add", "-b", branchName, path.toString(), baseBranch);
        }

        Workspace workspace = new Workspace(runId, path.toString(), branchName);
        acquired.put(runId, workspace);
        return workspace;
    }

    @Override
    public synchronized void cleanup(String runId) {
        Workspace workspace = acquired.remove(runId);
        if (workspace == null) {
            return;
        }
        log.info("[Workspace] worktree 제거: runId={}, path={}", runId, workspace.path());
        run(sourceRepo, "git", "worktree", "remove", "--force", workspace.path());
    }

    private Path resolveWorkspacePath(String runId) {
        Path candidate = runtimeRoot.resolve(runId).normalize();
        if (!candidate.startsWith(runtimeRoot)) {
            throw new IllegalArgumentException("Workspace path escapes runtime root for runId: " + runId);
        }
        return candidate;
    }

    private static String run(Path workDir, String... cmd) {
        AtomicReference<String> outputRef = new AtomicReference<>("");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(new File(workDir.toString()))
                    .redirectErrorStream(true);

            Process process = pb.start();

            Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    outputRef.set(new String(process.getInputStream().readAllBytes()));
                } catch (IOException ignored) {
                }
            });

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.interrupt();
                throw new IllegalStateException(cmd[0] + " timed out");
            }
            reader.join();

            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        String.join(" ", cmd) + " failed: " + outputRef.get().trim());
            }
            return outputRef.get();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(String.join(" ", cmd) + " error", e);
        }
    }
}
