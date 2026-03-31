package com.example.worker.agent.application.port;

import com.example.worker.agent.domain.model.AgentJobId;

public interface AgentExecutor {
    String execute(String workDir, String prompt, AgentJobId jobId);
}
