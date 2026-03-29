package com.example.worker.agent.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentLog")
class AgentLogTest {

    private final AgentJobId jobId = AgentJobId.newId();

    @Nested
    @DisplayName("팩토리 메서드")
    class FactoryMethods {

        @Test
        @DisplayName("text() 로 TEXT 타입 로그를 생성한다")
        void text_createsTextLog() {
            AgentLog log = AgentLog.text(jobId, "이슈 분석 중...");
            assertThat(log.type()).isEqualTo(LogType.TEXT);
            assertThat(log.content()).isEqualTo("이슈 분석 중...");
            assertThat(log.jobId()).isEqualTo(jobId);
            assertThat(log.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("toolUse() 로 TOOL_USE 타입 로그를 생성한다")
        void toolUse_createsToolUseLog() {
            AgentLog log = AgentLog.toolUse(jobId, "Write", "src/main/java/Foo.java");
            assertThat(log.type()).isEqualTo(LogType.TOOL_USE);
            assertThat(log.content()).contains("Write").contains("src/main/java/Foo.java");
        }

        @Test
        @DisplayName("statusChange() 로 STATUS_CHANGE 타입 로그를 생성한다")
        void statusChange_createsStatusChangeLog() {
            AgentLog log = AgentLog.statusChange(jobId, AgentJobStatus.CODING);
            assertThat(log.type()).isEqualTo(LogType.STATUS_CHANGE);
            assertThat(log.content()).isEqualTo("CODING");
        }
    }
}
