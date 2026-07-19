# Agent Worker 재구현 실행 계획

> 상태: 진행 중  
> 기준 설계: `docs/superpowers/specs/2026-07-17-polyglot-agent-worker-design.md`  
> 하네스 규약: `.agents/agent-worker-runtime.md`

## 목적

Java Control Plane/Temporal Engine과 격리된 Agent Worker의 경계를 재구현한다. 이번 계획은 P1 리뷰 지적을 먼저 차단하고, 공통 계약과 Worker host를 순차적으로 만든다.

## 고정 불변식

1. 승인 반려·수정 후 같은 단계를 다시 실행하면 **새 stage execution generation**을 사용한다. Temporal Activity 재시도만 같은 idempotency key를 재사용한다.
2. 원격 Worker를 사용하는 구현/QA 단계의 clone·worktree·Git/PR 작업은 같은 Worker 실행 환경에서 수행한다. Engine의 로컬 `WorkspaceRef`를 HTTP 계약으로 보내지 않는다.
3. 등록 저장소의 `.codex`/`.claude` 훅은 기본적으로 실행하지 않는다. 명시적 신뢰 정책을 통과한 허용 목록만 실행할 수 있다.
4. Control Plane이 `ProjectExecutionSnapshot`을 생성한다. 이 스냅샷에는 프로젝트·저장소·기준 브랜치·요청 커밋·자격증명 참조만 포함하며 비밀값·로컬 경로는 포함하지 않는다.

## 작업 단위

| 순서 | 책임 | 산출물 | 성공 기준 | 테스트 방법 | 커밋 |
|---|---|---|---|---|---|
| 01 | 실행 세대 식별 | 공유 `StageExecutionIdentity` 계약 | 반려 재개 시 같은 stage/attempt여도 다른 키 | contracts 단위 테스트 | `feat: add stage execution identity` |
| 02 | 외부 계약 | `agent-worker/v1` JSON schema와 Java client port | 경로·비밀·SDK 타입이 계약에 없음 | schema/forbidden-field 테스트 | `feat: add agent worker v1 contract` |
| 03 | 워커 소유 workspace | Worker-side clone/worktree/SCM 포트 | 구현·QA가 한 worktree를 재사용 | fake git integration test | `feat: make worker own workspace` |
| 04 | TypeScript 공통 host | durable ledger/event/cancel/harness snapshot | 재시작·중복 제출이 중복 프로세스를 만들지 않음 | conformance fixtures | `feat: add typescript worker host` |
| 05 | TypeScript adapters | Codex/Claude CLI·SDK | 네 어댑터가 공통 conformance 통과 | fake runtime tests | adapter별 커밋 |
| 06 | Python host/adapters | Python host와 네 어댑터 | TypeScript fixture와 JSON shape 동일 | shared fixture tests | host/adapter별 커밋 |
| 07 | Java CLI host | Java CLI host와 두 어댑터 | 동일 conformance 통과 | fake runtime tests | host/adapter별 커밋 |
| 08 | Engine 전환 | HTTP client를 통한 AI stages | assessment→planning→implementation→QA 순서 전환 | Temporal + fake worker test | stage별 커밋 |
| 09 | 전체 검증 | container E2E와 리뷰 | 하나의 worktree와 승인 게이트가 보존됨 | Testcontainers E2E | `test: verify remote worker flow` |

## 이번 루프: 작업 01

1. 실패 테스트를 추가한다: 같은 Activity 재시도는 같은 키를, stage revision은 다른 키를 가져야 한다.
2. 공유 `contracts` 모듈에 `StageExecutionIdentity`를 추가한다.
3. `ProjectExecutionSnapshot`을 별도 공유 계약으로 추가한다.
4. 기존 `engine.application.contract.v1`의 wire format은 변경하지 않는다. Engine HTTP client 전환 작업에서 새 identity를 사용한다.
5. contracts 집중 테스트를 실행하고, 실패하면 원인을 기록하고 다음 작업으로 넘어가지 않는다.

## 범위 제외

- 이번 커밋에서 Worker HTTP host·provider SDK·Git 클론 구현을 만들지 않는다.
- 기존 사용자의 frontend, `contracts/` 미추적 변경, console 문서를 수정하지 않는다.
