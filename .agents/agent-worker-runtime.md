# Agent Worker Runtime Harness

이 문서는 Control Plane/Agent Engine과 격리 Agent Worker 사이의 필수 운영 규약이다. `.agents/agent-team.md`의 공통 역할 계약에 추가로 적용한다.

## 실행 경계

- Engine은 Temporal 상태, 승인·반려, 재시도 정책만 소유한다.
- Worker는 clone, worktree, provider process/SDK, Git credential resolution, artifact upload를 소유한다.
- Worker HTTP 계약에는 로컬 절대 경로, API key/access token, provider SDK 객체를 넣지 않는다.
- Engine의 기존 `WorkspaceRef`는 Java 내부 마이그레이션 계약이다. 원격 Worker 요청으로 전달하거나 재해석하지 않는다.

## Idempotency 규약

- execution identity는 `workflowRunId`, stage, QA attempt, stage execution generation으로 구성한다.
- 같은 Activity의 Temporal retry만 동일 identity를 사용한다.
- 승인 반려·수정 후 재개는 대상 stage의 generation을 증가시킨다.

## Repository harness 신뢰 정책

- Worker는 등록 저장소의 instruction 파일을 스냅샷할 수 있지만 스냅샷 중 훅을 실행하지 않는다.
- `.codex`와 `.claude`의 hooks는 기본 거부다.
- 훅 실행은 프로젝트 등록 시 저장된 신뢰 정책과 명시적 경로 allowlist가 모두 있을 때만 가능하다.
- provider process에는 stage별 최소 권한과 allowlist 환경만 전달한다. Git·provider 비밀은 로그, context 문서, Engine history로 내보내지 않는다.
- 관련 구현은 "untrusted hook cannot read injected environment" 통합 테스트를 반드시 포함한다.

## 작업 루프

1. 한 작업은 한 책임·한 커밋이다.
2. 먼저 실패 테스트를 작성하고 확인한 뒤 최소 구현으로 통과시킨다.
3. 집중 테스트, 코드 품질 검사, 독립 리뷰를 거친다.
4. 테스트 또는 리뷰 실패 시 다음 작업으로 진행하지 않고 현재 작업을 수정한다.
