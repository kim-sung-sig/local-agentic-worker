package com.example.worker.agent.application.service;

import com.example.worker.agent.application.port.AgentLogStore;
import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentLog;
import com.example.worker.agent.infrastructure.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ClaudeAgentExecutor {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentProperties agentProperties;
    private final CommandRunner commandRunner;
    private final AgentLogStore logStore;

    @Autowired
    public ClaudeAgentExecutor(AgentProperties agentProperties, AgentLogStore logStore) {
        this(agentProperties,
                new ProcessRunner(agentProperties.getTimeoutMinutes(), TimeUnit.MINUTES),
                logStore);
    }

    ClaudeAgentExecutor(AgentProperties agentProperties, CommandRunner commandRunner, AgentLogStore logStore) {
        this.agentProperties = agentProperties;
        this.commandRunner = commandRunner;
        this.logStore = logStore;
    }

    public String execute(String workDir, String prompt) {
        return execute(workDir, prompt, AgentJobId.newId());
    }

    public String execute(String workDir, String prompt, AgentJobId jobId) {
        log.info("[Claude] 실행 시작 (workDir: {}, timeout: {}분)",
                workDir, agentProperties.getTimeoutMinutes());
        String rawOutput = commandRunner.run(workDir,
                agentProperties.getCliPath(), "--output-format", "stream-json",
                "--allowedTools", agentProperties.getAllowedTools(),
                "-p", prompt);
        String result = parseStreamJson(rawOutput, jobId);
        log.info("[Claude] 실행 완료 (result length: {})", result.length());
        return result;
    }

    private String parseStreamJson(String rawOutput, AgentJobId jobId) {
        String finalResult = rawOutput;
        for (String line : rawOutput.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JsonNode node = JSON.readTree(line);
                String type = node.path("type").asText();
                switch (type) {
                    case "assistant" -> {
                        JsonNode content = node.path("message").path("content");
                        if (content.isArray()) {
                            for (JsonNode item : content) {
                                if ("text".equals(item.path("type").asText())) {
                                    String text = item.path("text").asText();
                                    if (!text.isBlank()) {
                                        logStore.append(AgentLog.text(jobId, text));
                                    }
                                }
                            }
                        }
                    }
                    case "tool_use" -> {
                        String toolName = node.path("name").asText();
                        String input = node.path("input").toString();
                        logStore.append(AgentLog.toolUse(jobId, toolName, input));
                    }
                    case "result" -> finalResult = node.path("result").asText(rawOutput);
                }
            } catch (Exception ignored) {
                // 파싱 실패 라인은 무시
            }
        }
        return finalResult;
    }
}
