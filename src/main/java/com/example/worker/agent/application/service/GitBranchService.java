package com.example.worker.agent.application.service;

import com.example.worker.agent.application.exception.AgentExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GitBranchService {

    private static final Logger log = LoggerFactory.getLogger(GitBranchService.class);

    public void createBranch(String localPath, String baseBranch, String branchName) {
        log.info("[Git] 브랜치 생성 시작: {} (base: {}) → {}", localPath, baseBranch, branchName);
        runGit(localPath, "checkout", baseBranch);
        runGit(localPath, "pull", "origin", baseBranch);
        runGit(localPath, "checkout", "-b", branchName);
        log.info("[Git] 브랜치 생성 완료: {}", branchName);
    }

    private void runGit(String localPath, String... args) {
        try {
            List<String> cmd = new java.util.ArrayList<>();
            cmd.add("git");
            cmd.addAll(List.of(args));

            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(new File(localPath))
                    .redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new AgentExecutionException("git " + args[0] + " timed out");
            }
            if (process.exitValue() != 0) {
                throw new AgentExecutionException("git " + args[0] + " failed: " + output.trim());
            }
        } catch (AgentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentExecutionException("git " + args[0] + " error", e);
        }
    }
}
