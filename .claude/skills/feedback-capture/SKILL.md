---
name: feedback-capture
description: 작업 중 사용자가 내리는 피드백을 구조화하여 .skill-lab/feedback/YYYY-MM-DD-session.md에 append-only로 저장한다. /fb 또는 /fb flush로 호출한다. (/feedback은 Claude Code 내장 명령어이므로 사용 불가)
---

# feedback-capture

## 목적
- 작업 중 발생하는 즉각적인 피드백을 손실 없이 수집한다.
- 나중에 `/fb apply`가 처리할 수 있는 구조화된 형식으로 저장한다.
- 피드백이 규칙/스킬 개선으로 이어지는 순환 고리를 형성한다.

## 호출 원칙
- `/fb <피드백 내용>` — 단일 피드백 즉시 저장
- `/fb flush` — 현재 세션에서 구두로 오간 피드백을 일괄 정리 후 저장
- 대화 중 "그건 하지 마", "이렇게 해줘", "앞으로 ~" 패턴 감지 시 자동 제안
- ⚠️ `/feedback`은 Claude Code 내장 버그 리포트 명령어 — 절대 사용하지 않는다

## 입력
- 피드백 내용 (직접 입력 또는 대화에서 감지)
- 현재 작업 도메인/피처명 (컨텍스트)

## 출력
- `.skill-lab/feedback/YYYY-MM-DD-session.md` 파일에 항목 추가
- 저장 완료 확인 메시지 (항목 번호 + 카테고리)

## 저장 형식

파일 헤더 (첫 항목 추가 시):
```markdown
# 세션 피드백 — YYYY-MM-DD

> 이 파일은 append-only입니다. `/fb apply`로 처리합니다.
```

각 항목:
```markdown
## [HH:MM] 피드백 #N
- **컨텍스트**: {도메인명 또는 피처명}
- **피드백 내용**: {원문 그대로}
- **카테고리**: rule | skill | convention | behavior | other
- **영향 대상**: {예: rules/12-backend-code-convention.md, skills/plan/SKILL.md, 미정}
- **액션**: 즉시적용 | 검토필요 | 참고만
- **상태**: [ ] 미처리
```

### 카테고리 기준
- `rule`: 행동 방식, 접근 방식, 절차에 관한 피드백
- `skill`: 특정 스킬의 동작/출력에 관한 피드백
- `convention`: 코드 스타일, 네이밍, 포맷에 관한 피드백
- `behavior`: AI의 응답 방식, 말투, 설명 방식에 관한 피드백
- `other`: 위에 해당하지 않는 기타 피드백

## 워크플로우

### `/fb <내용>` 실행 시

1. 오늘 날짜로 파일 경로 결정: `.skill-lab/feedback/YYYY-MM-DD-session.md`
2. 파일이 없으면 헤더 포함하여 새로 생성
3. 기존 파일의 마지막 항목 번호 확인 (없으면 #1부터 시작)
4. 피드백 내용에서 카테고리 추론
5. 영향 대상 파일 추론 (확실하지 않으면 "미정" 기재)
6. 항목 append
7. 저장 완료 알림: `✓ fb #N 저장됨 (카테고리: {카테고리})`

### `/fb flush` 실행 시

1. 현재 대화에서 피드백으로 볼 수 있는 발언 목록 추출
2. 각 항목을 위 형식으로 정리하여 사용자에게 확인
3. 확인 후 일괄 append

## 게이트
- 피드백 내용이 비어 있지 않다.
- `.skill-lab/feedback/` 디렉터리가 존재한다.

## 중단 조건
- 피드백 내용이 없다 → "어떤 피드백을 저장할까요?"

## 차단 시 반환 형식
- 현재 stage: `feedback-capture`
- 차단 사유: 내용 없음
- 다음 필요 입력: 피드백 내용
