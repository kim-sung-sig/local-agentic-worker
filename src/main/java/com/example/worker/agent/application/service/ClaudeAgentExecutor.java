package com.example.worker.agent.application.service;

import com.example.worker.agent.infrastructure.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class ClaudeAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAgentExecutor.class);

    private final AgentProperties agentProperties;

    public ClaudeAgentExecutor(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    public String execute(String workDir, String prompt) {
        log.info("[Claude] 실행 시작 (workDir: {}, timeout: {}분)",
                workDir, agentProperties.getTimeoutMinutes());
        String output = ProcessRunner.run(workDir,
                agentProperties.getTimeoutMinutes(), TimeUnit.MINUTES,
                agentProperties.getCliPath(), "--print", "--dangerously-skip-permissions", "-p", prompt);
        log.info("[Claude] 실행 완료 (output length: {})", output.length());
        return output;
    }
}
