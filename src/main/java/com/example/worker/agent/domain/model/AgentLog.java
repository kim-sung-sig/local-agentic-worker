package com.example.worker.agent.domain.model;

import java.time.Instant;

public record AgentLog(
        AgentJobId jobId,
        Instant timestamp,
        LogType type,
        String content
) {
    public static AgentLog text(AgentJobId jobId, String text) {
        return new AgentLog(jobId, Instant.now(), LogType.TEXT, text);
    }

    public static AgentLog toolUse(AgentJobId jobId, String toolName, String input) {
        return new AgentLog(jobId, Instant.now(), LogType.TOOL_USE, toolName + ": " + input);
    }

    public static AgentLog statusChange(AgentJobId jobId, AgentJobStatus status) {
        return new AgentLog(jobId, Instant.now(), LogType.STATUS_CHANGE, status.name());
    }
}
