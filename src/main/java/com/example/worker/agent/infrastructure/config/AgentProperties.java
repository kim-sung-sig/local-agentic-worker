package com.example.worker.agent.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "agent.claude")
public class AgentProperties {

    private String cliPath = "claude";
    private int timeoutMinutes = 10;
    private String allowedTools = "Bash(git *),Bash(./gradlew *),Bash(npm *),Write(src/**),Edit(src/**),Read(**)";


}
