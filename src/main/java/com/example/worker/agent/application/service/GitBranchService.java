package com.example.worker.agent.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class GitBranchService {

    private static final Logger log = LoggerFactory.getLogger(GitBranchService.class);

    public void createBranch(String localPath, String baseBranch, String branchName) {
        log.info("[Git] 브랜치 생성 시작: {} (base: {}) → {}", localPath, baseBranch, branchName);
        ProcessRunner.run(localPath, 30, TimeUnit.SECONDS, "git", "checkout", baseBranch);
        ProcessRunner.run(localPath, 30, TimeUnit.SECONDS, "git", "pull", "origin", baseBranch);
        ProcessRunner.run(localPath, 30, TimeUnit.SECONDS, "git", "checkout", "-b", branchName);
        log.info("[Git] 브랜치 생성 완료: {}", branchName);
    }
}
