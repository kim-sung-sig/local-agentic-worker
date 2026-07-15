---
name: java-flow-step-comment
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.java$
  - field: new_text
    operator: regex_match
    pattern: (public|private|protected)\s+[\w<>\[\]]+\s+\w+\s*\([^)]*\)\s*\{
  - field: new_text
    operator: not_contains
    pattern: "// 1."
---

⚠️ **메서드 흐름 단계 주석 누락**

메서드를 작성/수정하려는데 흐름을 나타내는 `// 1.`부터 시작하는 단계 주석이 보이지 않습니다.

**참고 스타일:**

```java
@Transactional
public void cancelApproval(String approvalUid, String requestUserId, String cancelReason) {
    log.info(">>> [결재 취소] uid: {}, userId: {}", approvalUid, requestUserId);

    // 1. 결재 조회 및 잠금 - 취소는 상태 변경이므로 잠금 필요
    Approval approval = approvalRepository.findForUpdateByUidWithThrow(approvalUid);
    log.debug("[결재 취소] 현재 상태: {}, 요청자: {}", approval.getStatus(), requestUserId);

    // 2. 결재 회수 가능 확인
    checkRecallEligibility(approval, requestUserId);

    // 3. 상태 일괄 변경 (메인: RECALL, 상세 결정들: CANCEL)
    approval.cancel(cancelReason);
    log.debug("[결재 취소] 상태 변경 완료 - uid: {}, new status: {}", approval.getUid(), approval.getStatus());

    log.info("<<< [결재 취소 완료] uid: {}, userId: {}", approvalUid, requestUserId);
}
```

**규칙:**
- 메서드 본문의 주요 처리 단계마다 `// N. 설명` 형태로 번호를 매긴다 (`1.`부터 시작, 대괄호 `[N]` 표기는 쓰지 않는다).
- 설명은 "무엇을 왜 하는지"를 짧게 적는다 (예: "취소는 상태 변경이므로 잠금 필요").
- 각 단계 바로 아래에서 상태가 바뀌면 `log.debug(...)`로 그 결과를 남긴다 — 단계 주석과 로그를 한 쌍으로 취급한다.
- 단순 조회/단일 호출로 끝나는 짧은 메서드에는 강제하지 않는다.
