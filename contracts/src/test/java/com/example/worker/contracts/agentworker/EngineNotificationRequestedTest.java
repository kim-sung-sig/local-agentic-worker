package com.example.worker.contracts.agentworker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("EngineNotificationRequested")
class EngineNotificationRequestedTest {

    @Test
    @DisplayName("Engine이 발행하는 알림 요청은 workflowRunId/ticketId를 포함한 평문 레코드다")
    void carriesEngineAndTicketIdentity() {
        EngineNotificationRequested event = new EngineNotificationRequested(
                "wf-1", "11111111-1111-1111-1111-111111111111",
                "STAGE_COMPLETED", "INFO", "QA 통과", "QA 점수 95",
                "wf-1:QA:1:STAGE_COMPLETED:QA 통과:QA 점수 95",
                Instant.parse("2026-07-16T00:00:00Z"));

        assertEquals("wf-1", event.workflowRunId());
        assertEquals("11111111-1111-1111-1111-111111111111", event.ticketId());
        assertEquals("STAGE_COMPLETED", event.type());
        assertEquals("INFO", event.severity());
        assertEquals("QA 통과", event.title());
        assertEquals("QA 점수 95", event.message());
        assertEquals("wf-1:QA:1:STAGE_COMPLETED:QA 통과:QA 점수 95", event.idempotencyKey());
    }
}
