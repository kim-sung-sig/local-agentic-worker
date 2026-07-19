---
name: block-local-path-in-contract
enabled: true
event: file
action: block
conditions:
  - field: file_path
    operator: regex_match
    pattern: (?i)[\\/]contracts[\\/].*\.java$
  - field: new_text
    operator: regex_match
    pattern: \b(localPath|LocalPath|java\.nio\.file\.Path|Path)\b
---

**계약 DTO의 로컬 경로 노출 차단**

Control Plane은 원격 Git 저장소 정보만 전달한다. 실행 노드의 clone·worktree 경로는 Agent Engine Runtime 내부 책임이다.

- 계약에는 `repositoryUri`, `baseBranch`, `credentialRef` 같은 논리적 참조만 둔다.
- `localPath`와 `Path`는 DTO, 이벤트, 메시지에 넣지 않는다.
