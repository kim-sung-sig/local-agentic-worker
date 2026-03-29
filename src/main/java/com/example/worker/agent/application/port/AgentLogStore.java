package com.example.worker.agent.application.port;

import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentLog;

import java.util.List;
import java.util.function.Consumer;

public interface AgentLogStore {
    void append(AgentLog log);
    List<AgentLog> findByJobId(AgentJobId jobId);
    void registerSink(AgentJobId jobId, Consumer<AgentLog> sink);
    void unregisterSink(AgentJobId jobId);
}
