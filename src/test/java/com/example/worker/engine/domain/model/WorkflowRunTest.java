package com.example.worker.engine.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WorkflowRun")
class WorkflowRunTest {

    private WorkflowRun newRun() {
        return WorkflowRun.create(UUID.randomUUID(), "wf-" + UUID.randomUUID());
    }

    private AttemptRecord attempt(int attemptNumber, AttemptStatus status) {
        return new AttemptRecord(attemptNumber, "artifact-ref-" + attemptNumber,
                "qa-report-ref-" + attemptNumber, 90, status, Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("생성 시 INTAKE 상태이고 WorkspaceRef가 없다")
        void create_startsAtIntakeWithoutWorkspaceRef() {
            WorkflowRun run = newRun();

            assertThat(run.getCurrentStage()).isEqualTo(WorkflowStage.INTAKE);
            assertThat(run.getWorkspaceRef()).isNull();
            assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.RUNNING);
            assertThat(run.getAttempts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Stage 전이")
    class StageTransition {

        @Test
        @DisplayName("INTAKE -> PLANNING -> WORKSPACE -> IMPLEMENTATION -> QA -> REVIEW_MERGE 순차 전이가 성공한다")
        void advanceTo_sequentialTransitionsSucceed() {
            WorkflowRun run = newRun();

            run.advanceTo(WorkflowStage.PLANNING);
            run.advanceTo(WorkflowStage.WORKSPACE);
            run.advanceTo(WorkflowStage.IMPLEMENTATION);
            run.advanceTo(WorkflowStage.QA);
            run.advanceTo(WorkflowStage.REVIEW_MERGE);

            assertThat(run.getCurrentStage()).isEqualTo(WorkflowStage.REVIEW_MERGE);
        }

        @Test
        @DisplayName("QA -> IMPLEMENTATION 재시도 전이가 성공한다")
        void advanceTo_qaRetryToImplementationSucceeds() {
            WorkflowRun run = newRun();
            run.advanceTo(WorkflowStage.PLANNING);
            run.advanceTo(WorkflowStage.WORKSPACE);
            run.advanceTo(WorkflowStage.IMPLEMENTATION);
            run.advanceTo(WorkflowStage.QA);

            run.advanceTo(WorkflowStage.IMPLEMENTATION);

            assertThat(run.getCurrentStage()).isEqualTo(WorkflowStage.IMPLEMENTATION);
        }

        @Test
        @DisplayName("단계를 건너뛰는 전이는 거부된다")
        void advanceTo_skippingStageIsRejected() {
            WorkflowRun run = newRun();

            assertThatThrownBy(() -> run.advanceTo(WorkflowStage.IMPLEMENTATION))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("WorkspaceRef 할당")
    class WorkspaceRefAssignment {

        @Test
        @DisplayName("최초 1회 할당은 성공한다")
        void assignWorkspaceRef_firstCallSucceeds() {
            WorkflowRun run = newRun();

            run.assignWorkspaceRef("workspace-ref-1");

            assertThat(run.getWorkspaceRef()).isEqualTo("workspace-ref-1");
        }

        @Test
        @DisplayName("두 번째 할당 시도는 거부된다")
        void assignWorkspaceRef_secondCallIsRejected() {
            WorkflowRun run = newRun();
            run.assignWorkspaceRef("workspace-ref-1");

            assertThatThrownBy(() -> run.assignWorkspaceRef("workspace-ref-2"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Attempt 이력")
    class AttemptHistory {

        @Test
        @DisplayName("attempt 1, 2를 순차 기록하면 순서가 보존된다")
        void recordAttempt_sequentialAttemptsPreserveOrder() {
            WorkflowRun run = newRun();

            run.recordAttempt(attempt(1, AttemptStatus.FAILED));
            run.recordAttempt(attempt(2, AttemptStatus.PASSED));

            List<AttemptRecord> attempts = run.getAttempts();
            assertThat(attempts).hasSize(2);
            assertThat(attempts.get(0).attemptNumber()).isEqualTo(1);
            assertThat(attempts.get(1).attemptNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("attempt 번호를 건너뛰면 거부된다")
        void recordAttempt_skippingNumberIsRejected() {
            WorkflowRun run = newRun();

            assertThatThrownBy(() -> run.recordAttempt(attempt(2, AttemptStatus.FAILED)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("attempt 번호를 중복 기록하면 거부된다")
        void recordAttempt_duplicateNumberIsRejected() {
            WorkflowRun run = newRun();
            run.recordAttempt(attempt(1, AttemptStatus.FAILED));

            assertThatThrownBy(() -> run.recordAttempt(attempt(1, AttemptStatus.PASSED)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("getAttempts()가 반환한 리스트는 수정할 수 없다")
        void getAttempts_returnsImmutableList() {
            WorkflowRun run = newRun();
            run.recordAttempt(attempt(1, AttemptStatus.PASSED));

            List<AttemptRecord> attempts = run.getAttempts();

            assertThatThrownBy(() -> attempts.add(attempt(2, AttemptStatus.PASSED)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
