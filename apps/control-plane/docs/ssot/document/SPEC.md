# Document — SSoT

- 상태: template
- 최종 동기화: (미동기화)

> 규약: `docs/conventions/SSOT.md`. 서브도메인 **revision**은 `document/revision/SPEC.md` 참고.

## Purpose
이슈에 첨부되는 문서를 관리한다. 문서의 개정(revision)은 서브도메인으로 분리한다.

## Capabilities
-

## API surface
<!-- POST /api/issues/:issueId/documents, POST /api/documents/:documentId/revisions 등 -->

## Data model

## Invariants & rules

## Dependencies
- **issue** 도메인 (문서는 이슈에 첨부됨).
- 서브도메인: **document/revision**.

## Out of scope

## Source refs
- `apps/control-plane/server/utils/document-service.ts`
- `apps/control-plane/server/api/documents/`
- `apps/control-plane/server/api/issues/[issueId]/documents/`
