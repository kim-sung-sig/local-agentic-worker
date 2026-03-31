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

    // ─────────────────────────────────────────────
    // 단일 프롬프트 실행 (하위 호환)
    // ─────────────────────────────────────────────

    public String execute(String workDir, String prompt) {
        return execute(workDir, prompt, AgentJobId.newId());
    }

    public String execute(String workDir, String prompt, AgentJobId jobId) {
        return executePhase(workDir, prompt, jobId).output();
    }

    // ─────────────────────────────────────────────
    // 핑퐁 페이즈 실행 — sessionId 반환
    // ─────────────────────────────────────────────

    /**
     * 새 Claude 세션을 시작하고 PhaseResult(output + sessionId)를 반환한다.
     */
    public PhaseResult executePhase(String workDir, String prompt, AgentJobId jobId) {
        log.info("[Claude] Phase 시작 (workDir: {})", workDir);
        String rawOutput = commandRunner.run(workDir,
                agentProperties.getCliPath(),
                "--output-format", "stream-json",
                "--allowedTools", agentProperties.getAllowedTools(),
                "-p", prompt);
        PhaseResult result = parsePhaseResult(rawOutput, jobId);
        log.info("[Claude] Phase 완료 (sessionId: {}, resultLen: {})",
                result.sessionId(), result.output().length());
        return result;
    }

    /**
     * 이전 세션을 --resume으로 이어받아 다음 페이즈를 실행한다.
     * sessionId가 없으면 새 세션으로 폴백한다.
     */
    public PhaseResult resumePhase(String workDir, String prompt, String sessionId, AgentJobId jobId) {
        log.info("[Claude] Phase 재개 (sessionId: {})", sessionId);
        String rawOutput;
        if (sessionId != null && !sessionId.isBlank()) {
            rawOutput = commandRunner.run(workDir,
                    agentProperties.getCliPath(),
                    "--output-format", "stream-json",
                    "--allowedTools", agentProperties.getAllowedTools(),
                    "--resume", sessionId,
                    "-p", prompt);
        } else {
            log.warn("[Claude] sessionId 없음 — 새 세션으로 폴백");
            rawOutput = commandRunner.run(workDir,
                    agentProperties.getCliPath(),
                    "--output-format", "stream-json",
                    "--allowedTools", agentProperties.getAllowedTools(),
                    "-p", prompt);
        }
        PhaseResult result = parsePhaseResult(rawOutput, jobId);
        log.info("[Claude] Phase 재개 완료 (sessionId: {}, resultLen: {})",
                result.sessionId(), result.output().length());
        return result;
    }

    // ─────────────────────────────────────────────
    // stream-json 파싱
    // ─────────────────────────────────────────────

    private PhaseResult parsePhaseResult(String rawOutput, AgentJobId jobId) {
        String finalResult = rawOutput;
        String sessionId = null;

        for (String line : rawOutput.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JsonNode node = JSON.readTree(line);
                String type = node.path("type").asText();

                switch (type) {
                    case "system" -> {
                        // {"type":"system","subtype":"init","session_id":"..."}
                        String sub = node.path("subtype").asText();
                        if ("init".equals(sub) && node.has("session_id")) {
                            sessionId = node.path("session_id").asText();
                        }
                    }
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
                    case "result" -> {
                        finalResult = node.path("result").asText(rawOutput);
                        // result 이벤트에도 session_id 포함될 수 있음
                        if (sessionId == null && node.has("session_id")) {
                            sessionId = node.path("session_id").asText();
                        }
                    }
                }
            } catch (Exception ignored) {
                // 파싱 실패 라인 무시
            }
        }
        return new PhaseResult(finalResult, sessionId);
    }
}
