#!/usr/bin/env bash
# PreToolUse hook — Edit / Write 보호 파일 차단
# 입력: stdin에서 JSON (tool_input.file_path 포함)
# 종료 0 = 허용 / 종료 2 = 차단

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# 직접 편집을 차단할 보호 파일 목록
BLOCK_FILES=(
  ".claude/settings.json"
  ".claude/settings.local.json"
  "settings.gradle"
  "docker/compose.yml"
  "docker/.env"
)

for protected in "${BLOCK_FILES[@]}"; do
  if [[ "$FILE_PATH" == *"$protected"* ]]; then
    echo "[file-guard] ❌ 보호 파일 편집 차단: $FILE_PATH" >&2
    echo "[file-guard] 이 파일은 직접 수정이 제한되어 있습니다." >&2
    echo "[file-guard] 변경이 필요하다면 사용자에게 확인을 요청하세요." >&2
    exit 2
  fi
done

# 경고: 빌드 정의 파일은 수정 가능하지만 주의 필요
WARN_PATTERNS=(
  "build.gradle"
  "gradle.properties"
)

for pattern in "${WARN_PATTERNS[@]}"; do
  if [[ "$FILE_PATH" == *"$pattern"* ]]; then
    echo "[file-guard] ⚠️  빌드 설정 파일 수정: $FILE_PATH — 의존성·플러그인 변경은 신중히 하세요." >&2
    break
  fi
done

exit 0
