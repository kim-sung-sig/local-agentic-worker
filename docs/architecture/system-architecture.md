# Target System Architecture (Proposed)

이 문서는 현재 구현 현황이 아니라 승인 후 구축할 목표 아키텍처를 정의한다. 목표 시스템은 티켓을 정규화하고, AI 기반 개발 워크플로를 실행하며, 외부 개발 도구와 동기화한다.

시스템 다이어그램은 [Agent Worker System Diagram](../report/agent-worker-system-diagram.md)을 따른다.

## 1. Agent Worker Engine

Java와 Temporal로 구현하는 오케스트레이션 코어다. 실행 단계, 승인/반려, 재시도, 상태 이력만 책임진다. Git, 파일, 네트워크, 모델 호출은 직접 수행하지 않고 Temporal Activity 계약을 호출한다.

## 2. Agent Runtime and Adapters

프로젝트별 실행 노드가 worktree를 소유한다. Agent, QA, Source Control 구현체는 `WorkspaceRef`와 버전된 Activity DTO 계약을 통해 연결된다. 구현체는 Java, Python, TypeScript 등 어떤 언어로도 교체할 수 있다.

## 3. Ticket Sync

직접 등록과 GitHub Issues, Jira, Notion, Slack, Todo 등의 외부 입력을 정규화된 Ticket으로 변환한다. 구체적인 연동은 플러그인 구현체로 분리하며, Engine은 원본 시스템을 알지 못한다.

## Integration Rules

- Engine Workflow는 결정적 코드만 포함한다.
- CLI, API, Git, worktree, DB 조회 등 비결정적 I/O는 Activity에서 실행한다.
- Activity 입력과 출력은 버전된 JSON DTO이며, 대용량 산출물은 저장소 참조만 전달한다.
- 외부 변경 Activity는 Workflow ID와 단계/시도 횟수를 사용한 멱등 키를 가져야 한다.
