package com.example.worker.engine.application.contract.v1;

public record AttemptPolicy(int maxAttempts, int minimumQaScore, int version) {
}
