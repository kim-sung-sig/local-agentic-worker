package com.example.worker.agent.infrastructure.stream;

import com.example.worker.agent.application.port.AgentLogStore;
import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentLog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class InMemoryAgentLogStore implements AgentLogStore {

    private final ConcurrentHashMap<AgentJobId, CopyOnWriteArrayList<AgentLog>> logs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AgentJobId, Consumer<AgentLog>> sinks = new ConcurrentHashMap<>();

    @Override
    public void append(AgentLog log) {
        logs.computeIfAbsent(log.jobId(), k -> new CopyOnWriteArrayList<>()).add(log);
        Consumer<AgentLog> sink = sinks.get(log.jobId());
        if (sink != null) {
            sink.accept(log);
        }
    }

    @Override
    public List<AgentLog> findByJobId(AgentJobId jobId) {
        return new ArrayList<>(logs.getOrDefault(jobId, new CopyOnWriteArrayList<>()));
    }

    @Override
    public void registerSink(AgentJobId jobId, Consumer<AgentLog> sink) {
        sinks.put(jobId, sink);
    }

    @Override
    public void unregisterSink(AgentJobId jobId) {
        sinks.remove(jobId);
    }
}
