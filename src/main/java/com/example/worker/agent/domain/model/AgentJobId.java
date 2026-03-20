package com.example.worker.agent.domain.model;

import java.util.UUID;

public record AgentJobId(UUID value) {

    public static AgentJobId newId() {
        return new AgentJobId(UUID.randomUUID());
    }

    public static AgentJobId of(UUID value) {
        return new AgentJobId(value);
    }
}
