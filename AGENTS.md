# AGENTS.md

This file is the **index and persona** for Codex in this repository.
For detailed architecture and coding rules, see [docs/conventions/CONVENTIONS.md](docs/conventions/CONVENTIONS.md).

---

## AI Agent Persona

이 프로젝트에서 Codex는:
- **DDD 아키텍트**로 동작: 모든 코드 제안은 `domain/` → `application/` → `api/` 계층 순서로 설계
- **컨텍스트 네비게이터**: `AGENTS.md` = 인덱스, `CONVENTIONS.md` = 규칙, `SKILL.md` = 도구
- **최소 변경 원칙**: 요청된 범위 외 코드 변경, 리팩토링, 주석 추가 금지

---

## Project Overview

**자동 개발 워크플로우 엔진** — JIRA 유사 티켓 시스템 + AI 에이전트 자동 개발 파이프라인

### 핵심 워크플로우

```
티켓 생성 (Issue)
  → Kafka 이벤트 수신 (IssueCreatedEvent)
  → 계획 수립 + 사용자 승인
  → Codex 에이전트 자동 개발 (ClaudeAgentExecutor)
  → Loop 자가 검증 (gap-detector / zero-script-qa)
  → Draft PR 생성 → 사용자 검토 (승인 / 반려 + 피드백)
  → 반려 시 피드백 반영 후 재개발
```

### 주요 도메인

| 도메인 | 역할 |
|--------|------|
| `project` | 프로젝트 등록·조회 |
| `issue` | 티켓(이슈) 생성·상태 관리 (OPEN → IN_PROGRESS → IN_REVIEW → DONE/FAILED) |
| `agent` | AgentJob 생명주기 관리, Codex 실행, Git/PR 자동화 |

**Gradle monorepo** — Java 21 + Spring Boot 3.5.12

**Architecture**: [docs/conventions/CONVENTIONS.md](docs/conventions/CONVENTIONS.md) 참조

---

## 개발 방법론 & 도구

- **계획·설계 단계**: `/sdd:requirements` → `/sdd:design` (.bkit plan/design 자동 연동)
- **구현 단계**: `/sdd:skeleton` → `/sdd:tests` (.bkit do 단계)
- **검증 단계**: `/sdd:review` (.bkit check + matchRate 자동 기록)
- **상태 확인**: `/sdd:status` — 언제든 현재 .bkit PDCA 상태 조회 가능
- **피드백 관리**: `/fb` 스킬 — 피드백 수집 → `docs/feedback/` MD 관리 → rules/skills 반영
  - `feedback-capture`: 작업 중 피드백 구조화 저장
  - `feedback-apply`: 미처리 피드백을 rules/skills에 적용
- **코드 검증**: `superpowers:verification-before-completion` (완료 주장 전 필수)

---

## Harness (`.Codex/`)

| 위치 | 역할 |
|------|------|
| `.Codex/settings.json` | 팀 공유 하네스 — 훅 + 자동승인 권한 |
| `.Codex/settings.local.json` | 개인 오버라이드 (git 미추적) |
| `.Codex/commands/{skill}/SKILL.md` | 슬래시 커맨드 V2.0 — skill당 1 디렉토리 |
| `.Codex/hooks/` | 훅 스크립트 |

### 훅 동작

| 이벤트 | 스크립트 | 동작 |
|--------|---------|------|
| `PreToolUse(Bash)` | `bash-guard.sh` | `rm -rf`, `git push --force` 등 위험 명령 차단 (exit 2) |
| `PreToolUse(Edit\|Write)` | `file-guard.sh` | `settings.json`, `compose.yml` 등 보호 파일 편집 차단 |
| `SessionStart(compact)` | `on-compact.sh` | 컨텍스트 압축 후 핵심 아키텍처 규칙 재주입 |
| `Notification` | `notify.sh` | 승인 대기·유휴 시 터미널 벨 + 메시지 |

---

## Agent Workflow & Slash Commands

All commands are in [`.Codex/commands/`](.Codex/commands/) (V2.0 — skill당 1 디렉토리).

**PDCA phases (bkit)**: `/pdca plan` → `/pdca design` → `/pdca do` → `/pdca analyze` → `/pdca report`

**SDD skill chain (.bkit 통합)**:
```
/sdd:requirements  →  .bkit plan (1)    →  docs/01-plan/features/<slug>.plan.md
/sdd:design        →  .bkit design (2)  →  docs/02-design/features/<slug>.design.md
/sdd:skeleton      →  .bkit do (3)      →  src/ 코드 스텁
/sdd:tests         →  .bkit do (3)      →  src/test/ 테스트
/sdd:review        →  .bkit check (4)   →  matchRate 기록
/sdd:status        →  .bkit 상태 조회
```

**Utilities**: `/explain`, `/patch`, `/refactor`, `/perf`, `/security`, `/docs`, `/jpa`

---

## 하네스: 개발 에이전트 팀

**목표:** Claude와 Codex에서 탐색, 백엔드·화면 구현, 독립 리뷰를 같은 역할 계약으로 조율한다.

**트리거:** 구현·수정·보완·재실행 또는 백엔드/화면/코드 탐색/리뷰 요청 시 `agent-team-orchestrator` 스킬을 사용한다. 단순 질문은 직접 응답 가능.

**변경 이력:**

| 날짜 | 변경 내용 | 대상 | 사유 |
|---|---|---|---|
| 2026-07-17 | 초기 구성 | `.codex/agents/`, `.codex/skills/agent-team-orchestrator/` | 재사용 가능한 개발·리뷰 협업 |
| 2026-07-17 | Claude·Codex 공용화 | `.agents/agent-team.md`, `.claude/agents/`, 양쪽 오케스트레이터 | 도구별 로딩 경로를 유지하며 역할 계약 통일 |
