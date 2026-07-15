package com.example.worker.runtime.infrastructure.git;

import com.example.worker.runtime.application.WorkspaceRuntime;
import com.example.worker.runtime.application.WorkspaceRuntime.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GitWorktreeRuntime")
class GitWorktreeRuntimeTest {

    @TempDir
    Path sourceRepo;

    @TempDir
    Path runtimeRoot;

    private WorkspaceRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        run(sourceRepo, "git", "init", "-b", "main");
        run(sourceRepo, "git", "config", "user.email", "test@example.com");
        run(sourceRepo, "git", "config", "user.name", "Test");
        Files.writeString(sourceRepo.resolve("README.md"), "test repo");
        run(sourceRepo, "git", "add", ".");
        run(sourceRepo, "git", "commit", "-m", "init");

        runtime = new GitWorktreeRuntime(sourceRepo.toString(), runtimeRoot.toString());
    }

    @Test
    @DisplayName("최초 acquire는 지정 브랜치로 worktree를 생성한다")
    void acquire_firstCall_createsWorktreeOnBranch() throws Exception {
        Workspace workspace = runtime.acquire("run-1", "feature/run-1", "main");

        assertThat(Path.of(workspace.path())).isDirectory();
        assertThat(currentBranch(Path.of(workspace.path()))).isEqualTo("feature/run-1");
    }

    @Test
    @DisplayName("같은 runId로 반복 acquire하면 git worktree add를 다시 실행하지 않고 동일 Workspace를 반환한다")
    void acquire_repeatedCall_returnsSameWorkspaceWithoutRerunningGit() {
        Workspace first = runtime.acquire("run-2", "feature/run-2", "main");
        Workspace second = runtime.acquire("run-2", "feature/run-2", "main");

        assertThat(second).isEqualTo(first);
        assertThat(worktreeCount(Path.of(first.path()))).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 runId를 다른 브랜치로 acquire하면 거부된다")
    void acquire_branchMismatch_isRejected() {
        runtime.acquire("run-3", "feature/run-3", "main");

        assertThatThrownBy(() -> runtime.acquire("run-3", "feature/other", "main"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("다른 runId는 서로 다른 브랜치/worktree를 갖는다")
    void acquire_differentRunIds_haveDistinctWorkspaces() {
        Workspace a = runtime.acquire("run-4a", "feature/run-4a", "main");
        Workspace b = runtime.acquire("run-4b", "feature/run-4b", "main");

        assertThat(a.path()).isNotEqualTo(b.path());
        assertThat(a.branchName()).isNotEqualTo(b.branchName());
    }

    @Test
    @DisplayName("runId에 경로 이탈(path traversal) 입력이 있으면 거부되고 git 명령이 실행되지 않는다")
    void acquire_pathTraversalRunId_isRejected() {
        assertThatThrownBy(() -> runtime.acquire("../../etc/evil", "feature/evil", "main"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(runtimeRoot.resolve("../etc/evil").normalize()).doesNotExist();
    }

    @Test
    @DisplayName("cleanup은 worktree 디렉터리를 제거한다")
    void cleanup_removesWorktreeDirectory() {
        Workspace workspace = runtime.acquire("run-5", "feature/run-5", "main");
        assertThat(Path.of(workspace.path())).isDirectory();

        runtime.cleanup("run-5");

        assertThat(Path.of(workspace.path())).doesNotExist();
    }

    @Test
    @DisplayName("동시 acquire 요청은 정확히 하나의 Workspace만 생성한다")
    void acquire_concurrentCalls_produceExactlyOneWorkspace() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            Callable<Workspace> task = () -> runtime.acquire("run-6", "feature/run-6", "main");
            List<Future<Workspace>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(task))
                    .toList();

            Set<Workspace> results = futures.stream()
                    .map(this::getUnchecked)
                    .collect(Collectors.toSet());

            assertThat(results).hasSize(1);
            assertThat(worktreeCount(Path.of(results.iterator().next().path()))).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    private Workspace getUnchecked(Future<Workspace> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String currentBranch(Path worktreePath) throws IOException, InterruptedException {
        return run(worktreePath, "git", "branch", "--show-current").trim();
    }

    private long worktreeCount(Path worktreePath) {
        try {
            // Compare canonical (real) paths — Windows short (8.3) vs long path names and
            // separator style otherwise make a plain string `contains` check unreliable.
            Path expected = worktreePath.toRealPath();
            String output = run(sourceRepo, "git", "worktree", "list", "--porcelain");
            return output.lines()
                    .filter(line -> line.startsWith("worktree "))
                    .map(line -> line.substring("worktree ".length()).trim())
                    .filter(p -> sameRealPath(p, expected))
                    .count();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean sameRealPath(String candidate, Path expected) {
        try {
            return Path.of(candidate).toRealPath().equals(expected);
        } catch (IOException e) {
            return false;
        }
    }

    private static String run(Path workDir, String... cmd) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmd)
                .directory(new File(workDir.toString()))
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished || process.exitValue() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", cmd) + "\n" + output);
        }
        return output;
    }
}
