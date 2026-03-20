package com.example.worker.agent.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class PullRequestService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestService.class);

    public void push(String localPath, String branchName) {
        log.info("[PR] push origin {}", branchName);
        ProcessRunner.run(localPath, 60, TimeUnit.SECONDS, "git", "push", "origin", branchName);
    }

    public String createDraftPr(String localPath, String baseBranch,
                                 String branchName, String title, String body) {
        log.info("[PR] Draft PR 생성: {} → {}", branchName, baseBranch);
        String truncatedBody = body.length() > 65000 ? body.substring(0, 65000) + "\n...(truncated)" : body;
        String output = ProcessRunner.run(localPath, 60, TimeUnit.SECONDS,
                "gh", "pr", "create",
                "--draft",
                "--base", baseBranch,
                "--head", branchName,
                "--title", title,
                "--body", truncatedBody);
        String prUrl = output.trim().lines()
                .filter(l -> l.startsWith("https://"))
                .findFirst()
                .orElse(output.trim());
        log.info("[PR] Draft PR 생성 완료: {}", prUrl);
        return prUrl;
    }
}
