package com.example.worker.agent.api.controller;

import com.example.worker.agent.application.port.AgentLogStore;
import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentLog;
import com.example.worker.agent.domain.model.LogType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/agent-jobs")
public class AgentJobStreamController {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30분

    private final AgentLogStore logStore;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentJobStreamController(AgentLogStore logStore) {
        this.logStore = logStore;
    }

    @GetMapping(value = "/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID jobId) {
        AgentJobId id = AgentJobId.of(jobId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        executor.execute(() -> {
            try {
                // 기존 로그 먼저 전송
                for (AgentLog existing : logStore.findByJobId(id)) {
                    emitter.send(SseEmitter.event()
                            .data(JSON.writeValueAsString(toPayload(existing)))
                            .reconnectTime(3000));
                }
                // 이후 실시간 로그 구독
                logStore.registerSink(id, agentLog -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .data(JSON.writeValueAsString(toPayload(agentLog)))
                                .reconnectTime(3000));
                        if (agentLog.type() == LogType.STATUS_CHANGE &&
                                (agentLog.content().equals("SUCCEEDED") || agentLog.content().equals("FAILED"))) {
                            emitter.send(SseEmitter.event().name("done").data(""));
                            logStore.unregisterSink(id);
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        logStore.unregisterSink(id);
                        emitter.completeWithError(e);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> logStore.unregisterSink(id));
        emitter.onTimeout(() -> logStore.unregisterSink(id));
        return emitter;
    }

    private record LogPayload(String type, String content, String timestamp) {}

    private LogPayload toPayload(AgentLog agentLog) {
        return new LogPayload(
                agentLog.type().name(),
                agentLog.content(),
                agentLog.timestamp().toString());
    }
}
