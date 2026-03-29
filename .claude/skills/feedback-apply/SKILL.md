---
name: feedback-apply
description: .skill-lab/feedback/ 에 쌓인 미처리 피드백을 분석하여 rules, skills 파일 수정을 제안하고 사용자 확인 후 적용한다. /fb apply로 호출하거나 주간 스케줄로 자동 실행한다. (/feedback은 Claude Code 내장 명령어이므로 사용 불가)
---

# feedback-apply

## 목적
- 쌓인 피드백을 실제 rules/skills 파일 변경으로 이어지게 한다.
- "일관성": 반복 피드백이 다음 세션부터 자동 적용된다.
- "맞춤형": 사용자별 패턴이 시스템에 누적된다.

## 호출 원칙
- `/fb apply` — 수동 실행
- `/schedule` 스킬로 주간 자동 실행 등록 권장 (`0 9 * * 1`)
- 미처리 피드백이 5개 이상 쌓이면 적용 권장 알림
- ⚠️ `/feedback`은 Claude Code 내장 버그 리포트 명령어 — 절대 사용하지 않는다

## 입력
- `.skill-lab/feedback/*.md` — 모든 세션 피드백 파일
- `.skill-lab/skills/*/SKILL.md` — 현재 실험 스킬 파일들
- `.skill-lab/rules/*.md` — 현재 실험 규칙 파일들
- (병합 후) `agent-instructions/skills/*/SKILL.md`, `agent-instructions/rules/*.md`

## 출력
- 변경 제안 목록 (사용자 확인 전)
- 사용자 확인 후: 실제 파일 수정
- `.skill-lab/feedback/applied-log.md` 에 이력 append
- 처리된 피드백 항목 `[x] 적용됨 YYYY-MM-DD` 마킹

## 워크플로우

### 1. 미처리 피드백 수집
```
.skill-lab/feedback/*.md 읽기
"상태: [ ] 미처리" 항목만 추출
```

### 2. 카테고리별 그룹화
```
rule     → 몇 개
skill    → 몇 개
convention → 몇 개
behavior → 몇 개
other    → 몇 개
```

### 3. 변경 제안 생성

각 피드백 그룹에 대해:
- `rule` 카테고리 → 영향 대상 rule 파일에 추가/수정 내용 제안
- `skill` 카테고리 → 영향 대상 SKILL.md 수정 내용 제안
- `convention` 카테고리 → 해당 convention rule 파일 수정 제안
- `behavior` 카테고리 → rules/02-behavioral-guardrails.md 또는 03-response-rules.md 수정 제안
- `other` → 사용자에게 처리 방향 물어봄

제안 형식:
```
## 변경 제안 #N
- 피드백: #{번호} - {원문 요약}
- 대상 파일: {파일 경로}
- 변경 내용:
  Before: {기존 내용 또는 없음}
  After:  {제안 내용}
- 적용 근거: {왜 이렇게 바꾸는지}
```

### 4. 사용자 확인

모든 제안을 출력 후:
```
총 {N}개 변경 제안이 있습니다.
[A] 전체 적용  [S] 선택 적용  [C] 취소
```

### 5. 적용 실행

사용자가 확인한 항목:
1. 해당 파일 수정 (append 또는 targeted edit)
2. `.skill-lab/feedback/applied-log.md` 에 아래 형식으로 append:
   ```markdown
   ## YYYY-MM-DD 적용 이력
   - 피드백 #{번호}: {원문 요약} → {대상 파일} 수정
   ```
3. 원본 피드백 항목 상태 업데이트:
   `[ ] 미처리` → `[x] 적용됨 YYYY-MM-DD`

### 6. 완료 보고
```
✓ {N}개 피드백 처리 완료
  - 수정된 파일: {목록}
  - applied-log.md 갱신됨
```

## 게이트
- 미처리 피드백이 1개 이상 있다.
- 영향 대상 파일이 식별됐다 (최소 1개).

## 중단 조건
- 미처리 피드백이 없다 → "처리할 미처리 피드백이 없습니다."
- 모든 피드백의 영향 대상이 "미정"이다 → 사용자에게 방향 확인 후 진행

## 차단 시 반환 형식
- 현재 stage: `feedback-apply`
- 차단 사유
- 다음 필요 입력
