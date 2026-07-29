---
name: build-verify-agent
description: "빌드 컴파일 검증 에이전트. 로컬에 gradlew가 있으면 ./gradlew compileJava, 없으면 ./mvnw compile을 실행하고 오류/경고를 분류한다. Serena LSP로 변경 파일 진단도 수행한다. verify-qa 파이프라인의 첫 단계."
---

# Build Verify Agent

## 핵심 역할

빌드 도구 선택 → 컴파일 실행 → 오류/경고 분류 → LSP 진단(선택) → `_workspace/01_build_result.md` 작성

## 빌드 도구 선택 (Gradle 우선, Maven 폴백)

- 저장소 루트에 `gradlew`(또는 Windows에서 `gradlew.bat`) 파일이 존재하면 **Gradle을 사용한다.**
  - `./gradlew compileJava --console=plain 2>&1 | grep -E "error:|BUILD (SUCCESSFUL|FAILED)"`
  - `error:` 포함 라인이 1건이라도 있으면 FAIL. `warning:` 라인은 분류만 하고 FAIL 처리하지 않는다.
  - `gradlew`는 pom.xml 기준으로 개인이 로컬에만 두는 컴파일 가속용 파일이다(`.gitignore` 대상, 커밋 안 됨). 없는 환경(CI, 다른 클론)에서는 자동으로 Maven 경로로 진행한다.
- `gradlew`가 없으면 **기존과 동일하게 Maven을 사용한다.**
  - `./mvnw compile 2>&1 | grep -E "^\[ERROR\]|^\[WARN\]"` 로만 출력을 읽는다. `tail -N` 방식 금지.
  - `[ERROR]` 1건이라도 있으면 FAIL 판정. `[WARN]`은 분류만 하고 FAIL 처리하지 않는다.

## 작업 원칙

- `_workspace/00_input.md` 가 있으면 읽어서 변경 파일 목록과 도메인 정보를 파악한다.
- 변경 파일이 명시된 경우 Serena `get_diagnostics_for_file` 로 LSP 진단을 추가 수행한다.

## 입력/출력 프로토콜

- 입력: `_workspace/00_input.md` (있으면 읽음, 없으면 git diff로 변경 파일 추론)
- 출력: `_workspace/01_build_result.md`

## 출력 파일 형식

```markdown
# Build Verify 결과

## 판정
PASS | FAIL

## 컴파일 오류
(없으면 "없음")

## 컴파일 경고
(없으면 "없음")

## LSP 진단
(변경 파일이 식별된 경우 파일별 진단 결과, 없으면 "미수행")

## 실행 시각
{ISO 8601}
```

## 에러 핸들링

- 사용한 빌드 도구(`gradlew`/`mvnw`) 실행 자체가 실패하면 FAIL + 사유를 `컴파일 오류` 섹션에 기록한다.
- 오류 0건이면 PASS.

## 협업

- 이전: 없음 (파이프라인 첫 단계)
- 다음: `_workspace/01_build_result.md` → test-qa-agent 가 읽는다
