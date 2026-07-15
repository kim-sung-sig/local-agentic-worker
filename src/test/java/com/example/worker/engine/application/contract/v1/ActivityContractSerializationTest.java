package com.example.worker.engine.application.contract.v1;

import com.example.worker.engine.domain.model.WorkflowStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Activity contract v1")
class ActivityContractSerializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<Class<?>> ALL_CONTRACT_RECORDS = List.of(
            ActivityRequestMetadata.class,
            WorkspaceRef.class,
            ArtifactRef.class,
            AttemptPolicy.class,
            QaResult.class,
            TicketAssessmentRequest.class,
            TicketAssessmentResponse.class,
            PlanningRequest.class,
            PlanningResponse.class,
            WorkspaceRequest.class,
            WorkspaceResponse.class,
            ImplementationRequest.class,
            ImplementationResponse.class,
            QaRequest.class,
            AttemptHistoryRequest.class,
            AttemptHistoryResponse.class,
            SourceControlRequest.class,
            SourceControlResponse.class,
            NotificationRequest.class,
            NotificationResponse.class
    );

    private static ActivityRequestMetadata metadata() {
        return new ActivityRequestMetadata("run-1", WorkflowStage.PLANNING, 1, 1);
    }

    private static ArtifactRef artifactRef() {
        return new ArtifactRef("artifact://plan/1", "IMPLEMENTATION_PLAN", 1);
    }

    private static WorkspaceRef workspaceRef() {
        return new WorkspaceRef("workspace://run-1", 1);
    }

    static Stream<Object> contractInstances() {
        return Stream.of(
                metadata(),
                workspaceRef(),
                artifactRef(),
                new AttemptPolicy(2, 90, 1),
                new QaResult(true, 95, artifactRef(), 1),
                new TicketAssessmentRequest(metadata(), "ticket-1", "raw spec", 1),
                new TicketAssessmentResponse("refined spec", "FEATURE", 1),
                new PlanningRequest(metadata(), "refined spec", 1),
                new PlanningResponse(artifactRef(), new AttemptPolicy(2, 90, 1), 1),
                new WorkspaceRequest(metadata(), "FEATURE", "agent-worker-engine-t03", 1),
                new WorkspaceResponse(workspaceRef(), "feature/agent-worker-engine-t03_260716", 1),
                new ImplementationRequest(metadata(), workspaceRef(), artifactRef(), 1),
                new ImplementationResponse(artifactRef(), 1),
                new QaRequest(metadata(), workspaceRef(), artifactRef(), 1),
                new AttemptHistoryRequest(metadata(), artifactRef(), artifactRef(), 95, "PASSED", 1),
                new AttemptHistoryResponse(true, 1),
                new SourceControlRequest(metadata(), workspaceRef(), "CREATE_DRAFT_PR", 1),
                new SourceControlResponse("https://example.com/pr/1", "DRAFT", 1),
                new NotificationRequest(metadata(), "slack", "attention needed", 1),
                new NotificationResponse(true, 1)
        );
    }

    @Nested
    @DisplayName("Jackson round-trip")
    class RoundTrip {

        @ParameterizedTest
        @MethodSource("com.example.worker.engine.application.contract.v1.ActivityContractSerializationTest#contractInstances")
        @DisplayName("모든 v1 계약 record는 직렬화 후 역직렬화하면 원본과 동일하다")
        void roundTrip_restoresEqualInstance(Object instance) throws Exception {
            String json = MAPPER.writeValueAsString(instance);
            Object restored = MAPPER.readValue(json, instance.getClass());

            assertThat(restored).isEqualTo(instance);
        }
    }

    @Nested
    @DisplayName("리플렉션 계약 규율")
    class ReflectionRules {

        @Test
        @DisplayName("모든 v1 record는 version 컴포넌트를 가진다")
        void everyRecord_hasVersionComponent() {
            for (Class<?> type : ALL_CONTRACT_RECORDS) {
                boolean hasVersion = Stream.of(type.getRecordComponents())
                        .anyMatch(c -> c.getName().equals("version") && c.getType() == int.class);
                assertThat(hasVersion)
                        .as("%s must declare an int 'version' component", type.getSimpleName())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("이름이 Request로 끝나는 모든 record는 ActivityRequestMetadata metadata 컴포넌트를 가진다")
        void everyRequestRecord_hasMetadataComponent() {
            for (Class<?> type : ALL_CONTRACT_RECORDS) {
                if (!type.getSimpleName().endsWith("Request")) {
                    continue;
                }
                boolean hasMetadata = Stream.of(type.getRecordComponents())
                        .anyMatch(c -> c.getName().equals("metadata")
                                && c.getType() == ActivityRequestMetadata.class);
                assertThat(hasMetadata)
                        .as("%s must declare an ActivityRequestMetadata 'metadata' component", type.getSimpleName())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("모든 계약 타입은 record이다")
        void everyContractType_isRecord() {
            for (Class<?> type : ALL_CONTRACT_RECORDS) {
                assertThat(type.isRecord()).as("%s must be a record", type.getSimpleName()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("멱등키")
    class IdempotencyKey {

        @Test
        @DisplayName("workflowRunId:stage:attemptNumber 형식을 반환한다")
        void idempotencyKey_formatsWorkflowRunStageAttempt() {
            ActivityRequestMetadata metadata = new ActivityRequestMetadata("run-42", WorkflowStage.QA, 3, 1);

            assertThat(metadata.idempotencyKey()).isEqualTo("run-42:QA:3");
        }
    }
}
