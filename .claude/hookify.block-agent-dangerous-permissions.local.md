---
name: block-agent-dangerous-permissions
enabled: true
event: file
action: block
conditions:
  - field: file_path
    operator: regex_match
    pattern: (?i)\.(java|ts|tsx|js|py)$
  - field: new_text
    operator: contains
    pattern: --dangerously-skip-permissions
---

**Agent Worker 권한 우회 차단**

`--dangerously-skip-permissions`는 저장소 하네스와 주입된 자격증명에 대한 권한 경계를 무력화한다.

- 읽기 단계는 read-only/plan 정책을 사용한다.
- 쓰기 단계는 할당된 worktree 및 provider 도구 allowlist로 제한한다.
- 예외가 필요하면 Worker 격리와 신뢰 정책을 별도 설계·승인한다.
