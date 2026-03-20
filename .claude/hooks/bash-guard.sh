#!/usr/bin/env bash
# PreToolUse hook — Bash 위험 명령 차단
# 입력: stdin에서 JSON (tool_input.command 포함)
# 종료 0 = 허용 / 종료 2 = 차단 (stderr 메시지를 Claude에게 전달)

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# 즉시 차단: 복구 불가능한 위험 패턴
BLOCK_PATTERNS=(
  "rm -rf"
  "rm -fr"
  "git push --force"
  "git push -f"
  "git reset --hard"
  "git checkout -- "
  "git clean -f"
  "git clean -fd"
  "DROP TABLE"
  "DROP DATABASE"
  "TRUNCATE"
  "> /dev/null 2>&1 &"       # 백그라운드에서 stderr까지 숨기는 패턴
  "chmod 777"
)

for pattern in "${BLOCK_PATTERNS[@]}"; do
  if echo "$COMMAND" | grep -qiF "$pattern"; then
    echo "[bash-guard] ❌ 위험 명령 차단: '$pattern' 포함됨" >&2
    echo "[bash-guard] 명령 전문: $COMMAND" >&2
    echo "[bash-guard] 사용자에게 직접 확인 후 수동으로 실행하세요." >&2
    exit 2
  fi
done

# 경고만: 프로덕션 영향 가능성 있는 패턴 (차단하지는 않음)
WARN_PATTERNS=(
  "git push"
  "docker compose down -v"
  "kubectl delete"
)

for pattern in "${WARN_PATTERNS[@]}"; do
  if echo "$COMMAND" | grep -qiF "$pattern"; then
    echo "[bash-guard] ⚠️  주의 명령 감지: '$pattern' — 의도한 실행인지 확인하세요." >&2
    break
  fi
done

exit 0
