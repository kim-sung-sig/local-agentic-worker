---
name: sdd-craft
description: >
  Craft an effective SDD (Software Design Document) from raw requirements.
  Use this skill:
    * When you need a high-quality SDD with Spec Checklist and domain modeling
    * When converting conversation notes, user stories, or chat logs into a structured SDD
    * Before running /spec-to-skeleton — ensures spec quality is high enough
    * When requirements are ambiguous and need domain event + CQRS decomposition
---

# SDD Craft Skill

Produces a production-grade SDD following the project template, enriched with
domain event modeling, CQRS decomposition, and a Spec Checklist.

---

## When to Use

Invoke `$sdd-craft` **before** `$sdd-requirements` when:
- Requirements are in raw / conversational form
- Domain events or edge cases are unclear
- You want to validate CQRS split before writing code

---

## Step 1 — Requirement Extraction

Extract these elements from raw input:

| Element | Question to answer |
|---------|-------------------|
| Actor | Who triggers the action? |
| Trigger | What event / user action starts it? |
| Invariant | What business rules must always hold? |
| Domain Event | What happened as a result? (past tense, e.g. `MessageScheduled`) |
| Error Case | What can go wrong? Which `ChatErrorCode`? |

---

## Step 2 — CQRS Decomposition

Split every use case into Command or Query:

```
Command (쓰기):
  - Input: Request DTO
  - Output: void or created ID
  - Side effect: DB write, Kafka event, domain event publish
  - Service: XxxCommandService

Query (읽기):
  - Input: cursor / filter parameters
  - Output: Response DTO (cursor-based)
  - Side effect: 없음
  - Service: XxxQueryService
```

---

## Step 3 — API Contract

For each use case, define:

```
Method:   POST / GET / PUT / DELETE / PATCH
Path:     /api/v1/<context>/<action>
Request:  { field: type, validations }
Response: { field: type }
HTTP 200/201/204/400/404/409
```

---

## Step 4 — Spec Checklist (CLAUDE.md required)

Every SDD must include this checklist:

```markdown
## Spec Checklist
- [ ] API 엔드포인트 (HTTP method, path, request/response body)
- [ ] Domain Entity / Value Object 변경사항
- [ ] Command / Query 분리 (CQRS)
- [ ] 이벤트 발행 여부 (도메인 이벤트)
- [ ] 예외 및 에러 케이스
- [ ] 테스트 시나리오 (단위 / 통합)
```

Do not proceed to `/spec-to-skeleton` until all items are checked or explicitly marked `N/A`.

---

## Step 5 — Write SDD

Output to `docs/specs/SDD_<slug>.md` following `docs/specs/SDD_TEMPLATE.md`.

Key rules:
- Every requirement must be **explicit and testable** (no vague statements)
- Domain language must match existing bounded contexts in `CONVENTIONS.md`
- Missing information → mark `TBD:` with blocking question
- Link to: Plan document, skeleton path, test path

---

## Output Checklist

Before handing off to `/spec-to-skeleton`, verify:

- [ ] SDD file exists at `docs/specs/SDD_<slug>.md`
- [ ] Spec Checklist is complete
- [ ] At least 1 happy path + 1 failure scenario per use case
- [ ] Domain events named in past tense
- [ ] CQRS split explicit

---

## Skill Connection Flow

```
raw requirements
  → $sdd-craft       (this skill — spec quality gate)
  → $sdd-requirements (template conformance)
  → $spec-to-skeleton (code skeleton)
  → $skeleton-to-tests (TDD tests)
  → $sdd-review      (implementation review)
  → $done-gate       (completion gate)
```
