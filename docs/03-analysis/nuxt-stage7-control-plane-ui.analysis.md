# Nuxt Stage 7 — Control Plane UI analysis

## Delivered boundary

The Nuxt dashboard, project board, and issue workspace consume the typed client in `apps/control-plane/app/composables/control-plane.ts`. It uses session cookies for these real Control Plane endpoints:

| UI flow | Endpoint |
| --- | --- |
| Register / sign in | `POST /api/auth/register`, `POST /api/auth/login` |
| Project dashboard | `GET /api/projects` |
| Project creation | `POST /api/projects` |
| Project board | `GET /api/projects/:projectId`, `GET /api/projects/:projectId/issues` |
| Issue creation | `POST /api/projects/:projectId/issues` |
| Issue workspace | `GET /api/issues/:issueId` |

`apps/control-plane/test/ui/control-plane-ui.test.ts` is compile/API-boundary coverage: it compiles the actual Vue SFCs through Vite and injects a deterministic client fetch mock. It does not mount a browser UI. It covers the AuthGate template, native required project fields and posted payload, dashboard-to-board and board-to-workspace route generation, and local mock-worker event progression.

Actual Playwright browser smoke evidence is recorded separately in [`_workspace_ui/08_task4_browser_smoke.md`](_workspace_ui/08_task4_browser_smoke.md). That run exercises the mounted UI with route-mocked Control Plane APIs.

## Mock and Stage 6 boundary

The issue workspace deliberately has no `/agent/`, Temporal, Gateway, or worker HTTP call. `createMockWorker()` holds pending/working/rejected events in client memory; approve, reject, and retry only change that local state.

Notifications are not wired for live UI updates. The existing notification stream is replay/keep-alive only; live broadcast, Worker Gateway affinity, real activity adapters, webhook delivery, and live worker progress remain Stage 6 work.

## Verification

- `npm run test` — passed: Control Plane 50 tests, Temporal worker 15, contracts 7, database 23.
- `npm run lint` — passed.
- `npm run typecheck` — passed for all workspaces.
- `./gradlew.bat build` — passed (23 tasks up-to-date).

The workspace test has no direct browser DOM dependency (`@vue/test-utils` and `happy-dom` are optional transitive packages, not direct dependencies), so its deterministic coverage remains Vite SFC compilation plus mocked API boundaries. The separate Playwright smoke provides the mounted-browser evidence without changing the unit-test dependency set.
