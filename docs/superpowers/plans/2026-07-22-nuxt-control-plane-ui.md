# Nuxt Control Plane UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the existing Vue control-panel screens into the Nuxt Control Plane and connect project and issue flows to the existing Nitro API, while rendering worker activity as an explicit client-side mock.

**Architecture:** Nuxt pages own the dashboard, project board, and issue workspace routes. A small typed composable owns authenticated cookie-based `$fetch` calls and maps only APIs that exist; the issue workspace stores draft text and mock worker decisions locally, so it never invokes the retired Agent API routes.

**Tech Stack:** Nuxt 4, Vue 3 Composition API, TypeScript, existing Nitro APIs, Vitest/@nuxt/test-utils.

## Global Constraints

- Reuse the existing `frontend/src` information architecture and visual CSS; do not keep the old Vue/Vite app wired into the new UI.
- Use real APIs only for register/login, projects, issues, documents, and notifications where endpoint contracts exist.
- Keep worker phases, worker activity timeline, and approval/rejection actions client-side mock data; do not add Agent API routes or Temporal calls.
- Every API call must include browser session cookies and show a recoverable unauthenticated/error state.
- Do not modify Java services, Temporal worker code, Docker/compose files, or Stage 6 Gateway/webhook/SSE behavior.
- Do not add dependencies: use Nuxt `$fetch`, `NuxtLink`, `useRoute`, and Vue primitives already installed.
- TDD: write focused failing tests before implementation. One task per commit. Do not stage unrelated existing harness churn.

---

## File Structure

```
apps/control-plane/app/app.vue                                  -- Nuxt shell and global stylesheet
apps/control-plane/app/assets/app.css                            -- adapted legacy control-panel styling
apps/control-plane/app/composables/control-plane.ts              -- typed real API client and mock worker state
apps/control-plane/app/components/AuthGate.vue                   -- registration/login gate for protected APIs
apps/control-plane/app/pages/index.vue                           -- dashboard and project creation
apps/control-plane/app/pages/projects/[projectId].vue            -- project board and issue creation
apps/control-plane/app/pages/projects/[projectId]/issues/[issueId].vue -- issue workspace with mock worker panel
apps/control-plane/test/ui/control-plane-ui.test.ts              -- page/composable API and mock-worker assertions
docs/03-analysis/nuxt-stage7-control-plane-ui.analysis.md        -- delivered UI/API boundary and Stage 6 gaps
```

### Task 1: Nuxt shell, typed API client, and authentication entry

**Files:**
- Modify: `apps/control-plane/app/app.vue`
- Create: `apps/control-plane/app/assets/app.css`
- Create: `apps/control-plane/app/composables/control-plane.ts`
- Create: `apps/control-plane/app/components/AuthGate.vue`
- Create: `apps/control-plane/test/ui/control-plane-ui.test.ts`

**Interfaces:**
- `useControlPlaneApi()` returns `register(input)`, `login(input)`, `listProjects()`, `getProject(id)`, `createProject(input)`, `listIssues(projectId)`, `getIssue(id)`, and `createIssue(projectId, input)`.
- `useMockWorker(issueId)` returns a ref of timeline events and `advance()` / `reject()` that only mutate client state.
- `AuthGate` emits `authenticated` after successful `POST /api/auth/register` or `POST /api/auth/login`.

- [ ] **Step 1: Write failing client tests**

```ts
it('calls only an existing project endpoint with credentials', async () => {
  const fetcher = vi.fn().mockResolvedValue([])
  const api = createControlPlaneApi(fetcher)
  await api.listProjects()
  expect(fetcher).toHaveBeenCalledWith('/api/projects', { credentials: 'include' })
})

it('advances mock worker activity without making an HTTP request', () => {
  const worker = createMockWorker('issue-1')
  worker.advance()
  expect(worker.events.value.at(-1)?.status).toBe('working')
})
```

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/control-plane -- control-plane-ui.test.ts`
Expected: FAIL because the composable does not exist.

- [ ] **Step 3: Implement minimal client and shell**

```ts
export const createControlPlaneApi = (fetcher = $fetch) => ({
  listProjects: () => fetcher('/api/projects', { credentials: 'include' }),
  createProject: (body: CreateProjectInput) => fetcher('/api/projects', { method: 'POST', body, credentials: 'include' }),
})
```

Implement the legacy side navigation/top bar in `app.vue`, import `~/assets/app.css`, render `<NuxtPage />`, and make the auth gate accessible before protected page data loads.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/control-plane -- control-plane-ui.test.ts && npm run typecheck --workspace @agentic-worker/control-plane`

```bash
git add apps/control-plane/app/app.vue apps/control-plane/app/assets/app.css apps/control-plane/app/composables/control-plane.ts apps/control-plane/app/components/AuthGate.vue apps/control-plane/test/ui/control-plane-ui.test.ts
git commit -m "feat: add Nuxt control plane shell and API client"
```

### Task 2: Dashboard and project board

**Files:**
- Create: `apps/control-plane/app/pages/index.vue`
- Create: `apps/control-plane/app/pages/projects/[projectId].vue`
- Modify: `apps/control-plane/test/ui/control-plane-ui.test.ts`

**Interfaces:**
- Dashboard derives issue counts from `listProjects()` and `listIssues(project.id)` and creates a project with `{ name, repositoryUri, baseBranch, credentialRef? }`.
- Project board loads `getProject(projectId)` and `listIssues(projectId)`, and creates an issue with `{ title, description?, priority }`.

- [ ] **Step 1: Write failing page assertions**

```ts
it('renders a project returned by the Control Plane API and links to its board', async () => {
  const html = await renderDashboardWith({ projects: [{ id: 'p1', name: 'Control Plane' }] })
  expect(html).toContain('/projects/p1')
})
```

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/control-plane -- control-plane-ui.test.ts`
Expected: FAIL because the Nuxt pages do not exist.

- [ ] **Step 3: Implement pages**

Adapt the legacy dashboard and project-board structure with `<NuxtLink>`, `useRoute`, `ref`, `computed`, and the Task 1 composable. Preserve project/issue search and forms. On `401`, show `AuthGate`; on loading/error, render an accessible status message. Do not use `axios`, Vue Router imports, or the legacy `AgentApi`.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/control-plane -- control-plane-ui.test.ts && npm run typecheck --workspace @agentic-worker/control-plane`

```bash
git add apps/control-plane/app/pages/index.vue apps/control-plane/app/pages/projects/[projectId].vue apps/control-plane/test/ui/control-plane-ui.test.ts
git commit -m "feat: connect Nuxt dashboard and project board"
```

### Task 3: Issue workspace with explicit mock worker

**Files:**
- Create: `apps/control-plane/app/pages/projects/[projectId]/issues/[issueId].vue`
- Modify: `apps/control-plane/test/ui/control-plane-ui.test.ts`

**Interfaces:**
- The page loads the current project, issue list, and selected issue through Task 1 API methods.
- Draft plan text uses `localStorage` only after mount, keyed by issue ID.
- Worker panel receives all its state and actions from `useMockWorker(issueId)` and labels itself `Mock worker`.

- [ ] **Step 1: Write failing mock-boundary tests**

```ts
it('labels the worker panel as mock and never exposes a worker HTTP endpoint', async () => {
  const html = await renderWorkspace('issue-1')
  expect(html).toContain('Mock worker')
  expect(html).not.toContain('/agent/')
})
```

- [ ] **Step 2: Run RED**

Run: `npm run test --workspace @agentic-worker/control-plane -- control-plane-ui.test.ts`
Expected: FAIL because the workspace page does not exist.

- [ ] **Step 3: Implement workspace**

Port the issue detail, issue rail, draft-plan editor, and activity panel from the legacy screen. Keep approve/reject/retry buttons as local mock transitions and show a toast stating that worker execution is mocked. Do not call document approval or nonexistent agent endpoints.

- [ ] **Step 4: Run GREEN and commit**

Run: `npm run test --workspace @agentic-worker/control-plane -- control-plane-ui.test.ts && npm run typecheck --workspace @agentic-worker/control-plane`

```bash
git add apps/control-plane/app/pages/projects/[projectId]/issues/[issueId].vue apps/control-plane/test/ui/control-plane-ui.test.ts
git commit -m "feat: add Nuxt issue workspace with mock worker"
```

### Task 4: Browser smoke coverage and analysis

**Files:**
- Modify: `apps/control-plane/test/ui/control-plane-ui.test.ts`
- Create: `docs/03-analysis/nuxt-stage7-control-plane-ui.analysis.md`

- [ ] **Step 1: Add integration assertions**

Cover registration/login gate display, project form validation, project-to-board navigation, issue-to-workspace navigation, and mock-worker state transition. Keep test data in the test's `$fetch` mock; do not start Temporal.

- [ ] **Step 2: Run full validation**

Run from repository root: `npm run test`, `npm run lint`, `npm run typecheck`, and PowerShell `./gradlew.bat build`.
Expected: all commands exit 0.

- [ ] **Step 3: Write analysis and commit**

Record real endpoints used, mock-only worker behavior, notifications not wired for live updates, and the Stage 6 dependency boundary.

```bash
git add apps/control-plane/test/ui/control-plane-ui.test.ts docs/03-analysis/nuxt-stage7-control-plane-ui.analysis.md
git commit -m "test: verify Nuxt control plane UI flows"
```

## Self-Review Notes

- **Spec coverage:** Tasks 1–3 move all three mounted legacy routes to Nuxt; Tasks 1–2 connect real project/issue APIs; Task 3 keeps the worker mock as requested; Task 4 verifies the browser-facing paths.
- **Intentional exclusions:** live Worker Gateway, real agent progress, live notification SSE broadcast, unavailable agent endpoints, and legacy Vue/Vite deletion are out of scope.
- **Type consistency:** the shared composable is the sole API boundary for all pages; mock worker transitions have no HTTP transport.
