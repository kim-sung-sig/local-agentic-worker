# Document · Revision (서브도메인) — SSoT

- 상태: template
- 최종 동기화: (미동기화)

> 상위 도메인: `document`. 규약: `docs/conventions/SSOT.md`.
> 이 파일은 도메인이 서브도메인을 폴더 중첩으로 표현하는 **실증 예시**다.

## Purpose
문서의 개정본(revision)을 생성하고 승인(approve)한다.

## Capabilities
-

## API surface
<!-- POST /api/documents/:documentId/revisions, POST /api/document-revisions/:revisionId/approve -->

## Data model

## Invariants & rules

## Dependencies
- 상위: **document**.

## Out of scope

## Source refs
- `apps/control-plane/server/api/documents/[documentId]/revisions/index.post.ts`
- `apps/control-plane/server/api/document-revisions/[revisionId]/approve.post.ts`
