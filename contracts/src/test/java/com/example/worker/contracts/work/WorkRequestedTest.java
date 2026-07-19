package com.example.worker.contracts.work;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("WorkRequested")
class WorkRequestedTest {

    @Test
    @DisplayName("같은 이슈는 중복 전달되어도 같은 Workflow ID를 사용한다")
    void usesIssueIdAsDeterministicWorkflowId() {
        // Given
        UUID issueId = UUID.fromString("3d6f0a1b-56ec-4350-9454-e33a55b21ad8");
        WorkRequested request = new WorkRequested(
                issueId,
                UUID.randomUUID(),
                URI.create("https://github.com/acme/catalog.git"),
                "main",
                "상품 검색 개선",
                Instant.parse("2026-07-16T00:00:00Z")
        );

        // When
        String workflowId = request.workflowId();

        // Then
        assertEquals("issue-3d6f0a1b-56ec-4350-9454-e33a55b21ad8", workflowId);
    }
}
