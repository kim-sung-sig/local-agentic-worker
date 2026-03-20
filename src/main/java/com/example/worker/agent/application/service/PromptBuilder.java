package com.example.worker.agent.application.service;

import com.example.worker.issue.event.model.IssueCreatedEvent;

public final class PromptBuilder {

    private PromptBuilder() {}

    public static String build(IssueCreatedEvent event) {
        String description = event.description() == null ? "(설명 없음)" : event.description();
        return """
                당신은 시니어 개발자입니다.

                ## 이슈
                - 번호: #%d
                - 제목: %s
                - 우선순위: %s
                - 설명:
                %s

                ## 작업 지시
                1. 현재 코드베이스를 분석하세요.
                2. 이슈를 해결하는 최소한의 코드 변경을 구현하세요.
                3. 기존 컨벤션과 아키텍처를 따르세요.
                4. 모든 변경 완료 후 반드시 다음 명령을 실행하세요:
                   git add -A && git commit -m "feat: #%d %s"
                """.formatted(
                event.issueNumber(), event.title(), event.priority(),
                description,
                event.issueNumber(), event.title()
        );
    }
}
