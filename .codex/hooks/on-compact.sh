#!/usr/bin/env bash
# SessionStart hook (matcher: compact) — 컨텍스트 압축 후 핵심 가이드라인 재주입
# Claude Code가 대화를 압축(compact)한 직후 실행되어, 잊혀진 컨텍스트를 복원합니다.

cat << 'EOF'
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[on-compact] 컨텍스트 재로드 — Chat Platform
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 프로젝트
- Spring Boot 3.4.4 + Java 21 (Virtual threads 활성화)
- Gradle 멀티모듈: chat-server(20001) | websocket-server(20002)

## 핵심 아키텍처 규칙 (전체 내용: docs/conventions/CONVENTIONS.md)
- DDD 레이어: domain → application → infrastructure → rest (역방향 금지)
- domain 레이어는 Spring/JPA 의존성 절대 금지
- CQRS: XxxCommandService(쓰기) / XxxQueryService(읽기, cursor pagination)
- 쓰기: source 데이터소스 / 읽기: replica 데이터소스

## 테스트 컨벤션
- @Nested per method, @DisplayName 한글, Given/When/Then
- @Mock + @InjectMocks (Mockito) — 도메인 객체는 Mock 금지

## 에이전트 팀
- planner / developer / reviewer / qa → .claude/agents/
- 슬래시 커맨드 목록 → AGENT_COMMANDS.md

## 자주 쓰는 명령
- 전체 테스트: ./gradlew test
- 단일 모듈: ./gradlew :apps:chat:chat-server:test
- 인프라 기동: cd docker && docker compose up -d
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EOF
