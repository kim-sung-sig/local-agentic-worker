package com.example.worker.agent.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentJob")
class AgentJobTest {

    private AgentJob newJob() {
        return AgentJob.create(UUID.randomUUID(), UUID.randomUUID(), "feat/test-branch");
    }

    @Nested
    @DisplayName("상태 전환")
    class StatusTransition {

        @Test
        @DisplayName("생성 시 PENDING 상태이다")
        void create_isPending() {
            AgentJob job = newJob();
            assertThat(job.getStatus()).isEqualTo(AgentJobStatus.PENDING);
        }

        @Test
        @DisplayName("startPlanning() 호출 시 PLANNING 상태로 전환된다")
        void startPlanning_transitionsToPlanning() {
            AgentJob job = newJob();
            job.startPlanning();
            assertThat(job.getStatus()).isEqualTo(AgentJobStatus.PLANNING);
        }

        @Test
        @DisplayName("startCoding() 호출 시 CODING 상태로 전환된다")
        void startCoding_transitionsToCoding() {
            AgentJob job = newJob();
            job.startPlanning();
            job.startCoding();
            assertThat(job.getStatus()).isEqualTo(AgentJobStatus.CODING);
        }

        @Test
        @DisplayName("startVerifying() 호출 시 VERIFYING 상태로 전환된다")
        void startVerifying_transitionsToVerifying() {
            AgentJob job = newJob();
            job.startPlanning();
            job.startCoding();
            job.startVerifying();
            assertThat(job.getStatus()).isEqualTo(AgentJobStatus.VERIFYING);
        }

        @Test
        @DisplayName("complete() 호출 시 SUCCEEDED 상태로 전환되고 prUrl이 저장된다")
        void complete_transitionsToSucceeded() {
            AgentJob job = newJob();
            job.startPlanning();
            job.startCoding();
            job.complete("https://github.com/org/repo/pull/1");
            assertThat(job.getStatus()).isEqualTo(AgentJobStatus.SUCCEEDED);
            assertThat(job.getPrUrl()).isEqualTo("https://github.com/org/repo/pull/1");
            assertThat(job.getFinishedAt()).isNotNull();
        }

        @Test
        @DisplayName("fail() 호출 시 FAILED 상태로 전환되고 에러 메시지가 저장된다")
        void fail_transitionsToFailed() {
            AgentJob job = newJob();
            job.startPlanning();
            job.fail("claude timed out");
            assertThat(job.getStatus()).isEqualTo(AgentJobStatus.FAILED);
            assertThat(job.getErrorMessage()).isEqualTo("claude timed out");
            assertThat(job.getFinishedAt()).isNotNull();
        }
    }
}
