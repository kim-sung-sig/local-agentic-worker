package com.example.worker.agent.application.service;

/**
 * Claude CLI 단일 페이즈 실행 결과.
 * output    : 페이즈 최종 텍스트 결과
 * sessionId : 다음 --resume 호출에 사용할 Claude 세션 ID
 */
public record PhaseResult(String output, String sessionId) {

    public boolean hasSession() {
        return sessionId != null && !sessionId.isBlank();
    }
}
