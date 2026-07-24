# 도메인 인덱스 (루트)

> SSoT 루트 인덱스. 어떤 앱에 어떤 도메인이 있는지 가리킨다.
> 규약: `docs/conventions/SSOT.md`. 각 앱의 상세 레지스트리는 앱별 `DOMAIN.md`.

| 앱 | 도메인 | 앱 레지스트리 | SSoT 상태 |
|----|--------|--------------|-----------|
| control-plane | issue, project, auth, document(+revision), notification | `apps/control-plane/docs/DOMAIN.md` | 파일럿 (issue·project seeded) |
| worker-gateway | — | (미편입) | — |
| temporal-worker | — | (미편입) | — |
| python-agent-worker | — | (미편입) | — |

새 앱을 SSoT에 편입하면 이 표에 행을 추가하고 `apps/{app}/docs/`에 `ssot/`, `walk/`, `DOMAIN.md`를 만든다.
