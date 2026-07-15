---
name: block-destructive-revert
enabled: true
event: bash
action: block
pattern: git\s+checkout\s+(--\s|\.$|\.\s|HEAD\s+--)|git\s+restore(?!.*--staged)|git\s+reset\s+--hard|git\s+clean\s+-[a-zA-Z]*f|rm\s+-rf|rm\s+-f\b
---

**되돌리기/파일 삭제 명령 차단**

`git checkout -- <path>`, `git restore <path>`, `git reset --hard`, `git clean -f`, `rm -rf`, `rm -f` 계열 명령은 실행 전 확인 없이 실행하지 않는다.

**문제:** 이 명령들은 커밋되지 않은 변경사항을 되돌릴 수 없이 삭제한다. 과거에 `git checkout --`으로 unstaged 변경사항(163줄)을 복구 불가능하게 날린 사고가 있었다.

**올바른 절차:**
1. 먼저 `git status`와 `git diff -- <path>`로 대상 파일에 미커밋 변경이 있는지 확인한다.
2. 미커밋 변경이 있으면 사용자에게 먼저 알리고 명시적 승인을 받는다.
3. 승인 없이는 이 종류의 명령을 실행하지 않는다.

승인을 받았다면 이 훅 메시지를 참고해 안전을 확인했다고 응답하고 진행하라.
