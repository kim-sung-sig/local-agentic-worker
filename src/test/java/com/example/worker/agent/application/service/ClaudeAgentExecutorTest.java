package com.example.worker.agent.application.service;

import com.example.worker.agent.application.port.AgentLogStore;
import com.example.worker.agent.domain.model.AgentJobId;
import com.example.worker.agent.domain.model.AgentLog;
import com.example.worker.agent.domain.model.LogType;
import com.example.worker.agent.infrastructure.config.AgentProperties;
import com.example.worker.agent.infrastructure.stream.InMemoryAgentLogStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClaudeAgentExecutor")
class ClaudeAgentExecutorTest {

    @Nested
    @DisplayName("execute()")
    class Execute {

        private ClaudeAgentExecutor executor(CommandRunner runner) {
            AgentProperties props = new AgentProperties();
            props.setCliPath("claude");
            props.setTimeoutMinutes(1);
            return new ClaudeAgentExecutor(props, runner, new InMemoryAgentLogStore());
        }

        @Test
        @DisplayName("실행 명령에 dangerously-skip-permissions 플래그가 포함되지 않아야 한다")
        void shouldNotUseDangerouslySkipPermissions() {
            List<String> capturedCmd = new ArrayList<>();
            String result = executor((workDir, cmd) -> {
                capturedCmd.addAll(Arrays.asList(cmd));
                return "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"ok\"}";
            }).execute("/tmp/work", "build the feature");

            assertThat(capturedCmd).doesNotContain("--dangerously-skip-permissions");
        }

        @Test
        @DisplayName("허용된 도구 목록을 --allowedTools 플래그로 전달해야 한다")
        void shouldPassAllowedToolsFlag() {
            List<String> capturedCmd = new ArrayList<>();
            AgentProperties props = new AgentProperties();
            props.setCliPath("claude");
            props.setTimeoutMinutes(1);
            props.setAllowedTools("Bash(git *),Write(src/**),Edit(src/**),Read(**)");
            ClaudeAgentExecutor ex = new ClaudeAgentExecutor(props, (workDir, cmd) -> {
                capturedCmd.addAll(Arrays.asList(cmd));
                return "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"ok\"}";
            }, new InMemoryAgentLogStore());

            ex.execute("/tmp/work", "build the feature");

            assertThat(capturedCmd).contains("--allowedTools");
        }

        @Test
        @DisplayName("workDir를 CommandRunner에 전달해야 한다")
        void shouldPassWorkDirToRunner() {
            List<String> capturedWorkDirs = new ArrayList<>();
            executor((workDir, cmd) -> {
                capturedWorkDirs.add(workDir);
                return "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"ok\"}";
            }).execute("/project/my-app", "prompt");

            assertThat(capturedWorkDirs).containsExactly("/project/my-app");
        }
    }

    @Nested
    @DisplayName("stream-json 파싱")
    class StreamJsonParsing {

        private AgentProperties props() {
            AgentProperties p = new AgentProperties();
            p.setCliPath("claude");
            p.setTimeoutMinutes(1);
            return p;
        }

        @Test
        @DisplayName("assistant text 이벤트를 TEXT 로그로 변환한다")
        void parsesAssistantTextEvent() {
            String streamOutput =
                "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"이슈 분석 중\"}]}}\n" +
                "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"완료\"}";
            CommandRunner runner = (workDir, cmd) -> streamOutput;
            AgentLogStore logStore = new InMemoryAgentLogStore();
            AgentJobId jobId = AgentJobId.newId();

            ClaudeAgentExecutor executor = new ClaudeAgentExecutor(props(), runner, logStore);
            executor.execute("/work", "prompt", jobId);

            List<AgentLog> logs = logStore.findByJobId(jobId);
            assertThat(logs).anyMatch(l -> l.type() == LogType.TEXT && l.content().contains("이슈 분석 중"));
        }

        @Test
        @DisplayName("tool_use 이벤트를 TOOL_USE 로그로 변환한다")
        void parsesToolUseEvent() {
            String streamOutput =
                "{\"type\":\"tool_use\",\"name\":\"Write\",\"input\":{\"file_path\":\"src/Foo.java\"}}\n" +
                "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"done\"}";
            CommandRunner runner = (workDir, cmd) -> streamOutput;
            AgentLogStore logStore = new InMemoryAgentLogStore();
            AgentJobId jobId = AgentJobId.newId();

            ClaudeAgentExecutor executor = new ClaudeAgentExecutor(props(), runner, logStore);
            executor.execute("/work", "prompt", jobId);

            List<AgentLog> logs = logStore.findByJobId(jobId);
            assertThat(logs).anyMatch(l -> l.type() == LogType.TOOL_USE && l.content().contains("Write"));
        }

        @Test
        @DisplayName("result 이벤트의 result 필드를 반환값으로 사용한다")
        void returnsResultFromResultEvent() {
            String streamOutput =
                "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"최종 완료 메시지\"}";
            CommandRunner runner = (workDir, cmd) -> streamOutput;

            ClaudeAgentExecutor executor = new ClaudeAgentExecutor(props(), runner, new InMemoryAgentLogStore());
            String result = executor.execute("/work", "prompt", AgentJobId.newId());

            assertThat(result).isEqualTo("최종 완료 메시지");
        }
    }
}
