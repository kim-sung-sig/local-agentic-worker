package com.example.worker.engine.application.service;

import com.example.worker.engine.application.contract.v1.AttemptPolicy;

/**
 * Pure, deterministic policy resolution — intentionally not a Spring bean so it can be
 * instantiated directly inside {@code AgentWorkerWorkflowImpl} (Temporal Workflow code
 * does not receive Spring-injected dependencies).
 */
public class AttemptPolicyResolver {

    public static final int DEFAULT_MAX_ATTEMPTS = 2;
    public static final int DEFAULT_MINIMUM_QA_SCORE = 90;

    private static final int MIN_ATTEMPTS_BOUND = 1;
    private static final int MAX_ATTEMPTS_BOUND = 10;
    private static final int MIN_QA_SCORE_BOUND = 0;
    private static final int MAX_QA_SCORE_BOUND = 100;

    /**
     * A non-positive {@code maxAttempts}/{@code minimumQaScore} in {@code raw} is treated as
     * "unset" and replaced with the default; any other out-of-range value is rejected.
     */
    public AttemptPolicy resolve(AttemptPolicy raw) {
        int maxAttempts = raw.maxAttempts() <= 0 ? DEFAULT_MAX_ATTEMPTS : raw.maxAttempts();
        int minimumQaScore = raw.minimumQaScore() <= 0 ? DEFAULT_MINIMUM_QA_SCORE : raw.minimumQaScore();

        if (maxAttempts < MIN_ATTEMPTS_BOUND || maxAttempts > MAX_ATTEMPTS_BOUND) {
            throw new IllegalArgumentException(
                    "maxAttempts must be between " + MIN_ATTEMPTS_BOUND + " and " + MAX_ATTEMPTS_BOUND
                            + " but was " + maxAttempts);
        }
        if (minimumQaScore < MIN_QA_SCORE_BOUND || minimumQaScore > MAX_QA_SCORE_BOUND) {
            throw new IllegalArgumentException(
                    "minimumQaScore must be between " + MIN_QA_SCORE_BOUND + " and " + MAX_QA_SCORE_BOUND
                            + " but was " + minimumQaScore);
        }

        return new AttemptPolicy(maxAttempts, minimumQaScore, raw.version());
    }
}
