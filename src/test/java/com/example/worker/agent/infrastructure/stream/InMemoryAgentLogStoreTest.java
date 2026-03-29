package com.example.worker.agent.infrastructure.stream;

import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryAgentLogStore")
class InMemoryAgentLogStoreTest {

    private InMemoryAgentLogStore store;
    private AgentJobId jobId;

    @BeforeEach
    void setUp() {
        store = new InMemoryAgentLogStore();
        jobId = AgentJobId.newId();
    }

    @Nested
    @DisplayName("append / findByJobId")
    class AppendAndFind {

        @Test
        @DisplayName("로그를 추가하면 jobId로 조회할 수 있다")
        void append_thenFindByJobId() {
            store.append(AgentLog.text(jobId, "분석 중"));
            store.append(AgentLog.text(jobId, "코딩 중"));

            List<AgentLog> logs = store.findByJobId(jobId);
            assertThat(logs).hasSize(2);
            assertThat(logs.get(0).content()).isEqualTo("분석 중");
        }

        @Test
        @DisplayName("다른 jobId의 로그는 조회되지 않는다")
        void findByJobId_doesNotReturnOtherJobLogs() {
            AgentJobId otherId = AgentJobId.newId();
            store.append(AgentLog.text(jobId, "내 로그"));
            store.append(AgentLog.text(otherId, "다른 로그"));

            assertThat(store.findByJobId(jobId)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("sink 등록")
    class SinkRegistration {

        @Test
        @DisplayName("sink를 등록하면 append 시 즉시 호출된다")
        void registerSink_invokedOnAppend() {
            List<AgentLog> received = new ArrayList<>();
            store.registerSink(jobId, received::add);

            store.append(AgentLog.text(jobId, "실시간 로그"));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).content()).isEqualTo("실시간 로그");
        }

        @Test
        @DisplayName("sink를 해제하면 이후 append에서 호출되지 않는다")
        void unregisterSink_notInvokedAfterUnregister() {
            List<AgentLog> received = new ArrayList<>();
            store.registerSink(jobId, received::add);
            store.unregisterSink(jobId);

            store.append(AgentLog.text(jobId, "해제 후 로그"));

            assertThat(received).isEmpty();
        }
    }
}
