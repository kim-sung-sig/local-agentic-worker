package com.example.worker.agent.application.service;

import com.example.worker.agent.application.exception.AgentExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Service
public class PullRequestService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestService.class);

    public void push(String localPath, String branchName) {
        log.info("[PR] push origin {}", branchName);
        run(localPath, "git", "push", "origin", branchName);
    }

    public String createDraftPr(String localPath, String baseBranch,
                                 String branchName, String title, String body) {
        log.info("[PR] Draft PR 생성: {} → {}", branchName, baseBranch);
        String output = run(localPath,
                "gh", "pr", "create",
                "--draft",
                "--base", baseBranch,
                "--head", branchName,
                "--title", title,
                "--body", body.length() > 65000 ? body.substring(0, 65000) + "\n...(truncated)" : body
        );
        String prUrl = output.trim().lines()
                .filter(l -> l.startsWith("https://"))
                .findFirst()
                .orElse(output.trim());
        log.info("[PR] Draft PR 생성 완료: {}", prUrl);
        return prUrl;
    }

    private String run(String localPath, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args)
                    .directory(new File(localPath))
                    .redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new AgentExecutionException(args[0] + " " + args[1] + " timed out");
            }
            if (process.exitValue() != 0) {
                throw new AgentExecutionException(args[0] + " " + args[1] + " failed: " + output.trim());
            }
            return output;
        } catch (AgentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentExecutionException(args[0] + " execution error", e);
        }
    }
}
