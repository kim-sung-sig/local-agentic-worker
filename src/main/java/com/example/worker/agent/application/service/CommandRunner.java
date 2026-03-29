package com.example.worker.agent.application.service;

@FunctionalInterface
interface CommandRunner {
    String run(String workDir, String... cmd);
}
