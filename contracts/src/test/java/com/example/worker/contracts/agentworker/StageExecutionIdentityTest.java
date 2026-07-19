package com.example.worker.contracts.agentworker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DisplayName("StageExecutionIdentity")
class StageExecutionIdentityTest {

    @Test
    @DisplayName("같은 Activity 재시도는 같은 키를, 반려 재개는 다른 키를 사용한다")
    void separatesActivityRetryFromRevision() {
        StageExecutionIdentity retry = new StageExecutionIdentity("run-1", "PLANNING", 1, 1);
        StageExecutionIdentity revision = new StageExecutionIdentity("run-1", "PLANNING", 1, 2);

        assertEquals(retry.idempotencyKey(), new StageExecutionIdentity("run-1", "PLANNING", 1, 1).idempotencyKey());
        assertNotEquals(retry.idempotencyKey(), revision.idempotencyKey());
    }
}
