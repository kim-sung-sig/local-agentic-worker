---
name: java-service-method-logging
enabled: true
event: file
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.java$
  - field: new_text
    operator: regex_match
    pattern: "@Transactional[^\n]*\\s*\\n\\s*public\\s+[\\w<>\\[\\]]+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{"
  - field: new_text
    operator: not_contains
    pattern: ">>>"
---

⚠️ **`@Transactional` 서비스 메서드에 진입/종료 로그 누락**

`@Transactional` 메서드를 작성/수정하려는데 진입 로그(`>>>`)가 보이지 않습니다.

**참고 스타일:**

```java
/**
 * 결재 취소 (회수)
 */
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
- 메서드 시작부에 `log.info(">>> [작업명] ...")`로 진입 로그를 남긴다. 파라미터 중 식별에 필요한 값(uid, userId 등)을 포함한다.
- 메서드 종료 직전에 `log.info("<<< [작업명 완료] ...")`로 종료 로그를 남긴다. 대괄호 안 작업명은 진입 로그와 동일하게 맞춘다.
- 상태가 바뀌는 지점마다 `log.debug("[작업명] ...")`로 중간 상태를 남긴다.
- 단계 주석은 `// N. 설명` 형태(점 표기)로 통일한다. `// [N]` 대괄호 표기는 섞어 쓰지 않는다.
- 로그 레벨: 시작/종료는 `log.info`, 중간 상태·디버깅용 세부는 `log.debug`.
