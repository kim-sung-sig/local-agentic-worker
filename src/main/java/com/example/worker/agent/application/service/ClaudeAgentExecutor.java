package com.example.worker.agent.application.service;

import com.example.worker.agent.application.exception.AgentExecutionException;
import com.example.worker.agent.infrastructure.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
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
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    agentProperties.getCliPath(),
                    "--print",
                    "--dangerously-skip-permissions",
                    "-p", prompt
            )
                    .directory(new File(workDir))
                    .redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());

            boolean finished = process.waitFor(agentProperties.getTimeoutMinutes(), TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new AgentExecutionException("Claude CLI timed out after "
                        + agentProperties.getTimeoutMinutes() + " minutes");
            }
            if (process.exitValue() != 0) {
                throw new AgentExecutionException("Claude CLI exited with code "
                        + process.exitValue() + ": " + truncate(output));
            }

            log.info("[Claude] 실행 완료 (output length: {})", output.length());
            return output;
        } catch (AgentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentExecutionException("Claude CLI execution error", e);
        }
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
