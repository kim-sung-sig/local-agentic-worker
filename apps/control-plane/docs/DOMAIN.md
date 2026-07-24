# control-plane — 도메인 레지스트리

> 앱 레지스트리. 규약: `docs/conventions/SSOT.md`. 루트 인덱스: `docs/DOMAIN.md`.
> 각 도메인 진실원은 ssot 폴더 아래 **4종 파일**: `SPEC.md`(능력) · `CONTEXT.md`(바운디드 컨텍스트) · `MODEL.md`(도메인 모델) · `LANGUAGE.md`(유비쿼터스 언어).

| 도메인 | 서브도메인 | ssot 폴더 | walk | status |
|--------|-----------|-----------|------|--------|
| issue (티켓) | — | `docs/ssot/issue/` | `docs/walk/issue/` | seeded |
| project | — | `docs/ssot/project/` | `docs/walk/project/` | seeded |
| auth | — | `docs/ssot/auth/` | `docs/walk/auth/` | template |
| document | revision | `docs/ssot/document/` | `docs/walk/document/` | template |
| document · revision | (상위: document) | `docs/ssot/document/revision/` | `docs/walk/document/` | template |
| notification | — | `docs/ssot/notification/` | `docs/walk/notification/` | template |

경로는 이 파일(`apps/control-plane/docs/`) 기준 상대경로. `status=seeded`는 4종 파일이 코드 기반으로 채워짐, `template`은 골격만.

## 새 feature 시작
```bash
node scripts/ssot/walk-new.mjs control-plane <domain> <feature-slug>
```
