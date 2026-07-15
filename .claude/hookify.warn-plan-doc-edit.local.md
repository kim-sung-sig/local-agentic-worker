---
name: warn-plan-doc-edit
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: plan\.md$
---

**plan.md 파일 편집 감지**

`*plan.md` 파일을 수정하고 있습니다.

**규칙:** 구현 전(pre-implementation) 단계의 plan 문서는 append-only가 아니라 직접 수정(재작성/병합)한다.

| 단계 | 방식 |
|------|------|
| 구현 전 계획 수정 | 기존 내용 직접 Edit/병합 |
| 구현 후 walkthrough·실행결과 기록 | append-only |

**판단 기준:** 이 파일에 이미 실행 결과(walkthrough, QA 기록)가 포함되어 있으면 append, 아직 계획만 있으면 직접 수정.
