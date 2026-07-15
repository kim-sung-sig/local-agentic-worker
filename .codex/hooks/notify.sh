#!/usr/bin/env bash
# Notification hook — Claude Code가 사용자 입력 대기 상태가 될 때 알림
# permission_prompt: 도구 실행 승인 필요 / idle_prompt: Claude가 대기 중

INPUT=$(cat)
NOTIFICATION_TYPE=$(echo "$INPUT" | jq -r '.notification_type // empty')

case "$NOTIFICATION_TYPE" in
  permission_prompt)
    # 터미널 벨 + 메시지 출력
    printf '\a' >&2
    echo "[notify] 🔔 Claude가 도구 실행 승인을 기다리고 있습니다." >&2
    ;;
  idle_prompt)
    printf '\a' >&2
    echo "[notify] 💬 Claude가 추가 지시를 기다리고 있습니다." >&2
    ;;
  *)
    ;;
esac

exit 0
