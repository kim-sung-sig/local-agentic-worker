# ssot — 단일 공급 진실원 운영 (Codex)

이 프롬프트는 로직을 담지 않는다. **모든 규칙은 공유 규약 한 곳에 있다.** (Claude용 `.claude/commands/ssot/SKILL.md`와 동일 절차, 내용은 복제하지 않는다.)

## 절차
1. **`docs/conventions/SSOT.md`를 먼저 읽는다** (ssot·walk 정의, 구조, 라이프사이클).
2. 도메인 능력을 알아야 하면 진실원을 읽는다: `apps/{app}/docs/ssot/{domain}/SPEC.md`.
   - 어떤 도메인이 어디 있는지는 `docs/DOMAIN.md`(루트) → `apps/{app}/docs/DOMAIN.md`(앱).
3. 새 feature 작업공간을 만들 때는 **공유 스크립트**를 실행한다(직접 mkdir 금지):
   ```bash
   node scripts/ssot/walk-new.mjs <app> <domain> <feature-slug>
   ```
4. 개발 후 도메인 능력이 바뀌었으면 해당 `SPEC.md`를 코드에 맞춰 갱신하고 "최종 동기화" 줄을 갱신한다.

플랫폼 무관 자산은 `docs/`·`scripts/`에만 존재한다. 이 파일은 Codex용 얇은 진입점일 뿐이다.
