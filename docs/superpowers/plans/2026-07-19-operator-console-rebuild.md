# Operator Console Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing frontend with a dashboard, project Kanban board, issue drawer, and project notification Inbox.

**Architecture:** Project and Issue REST APIs provide live dashboard/board data. A pure presentation module maps issue and optional workflow state to exactly one lane. Drawer workflow actions remain disabled until an Issue response supplies `workflowRunId`; project SSE only updates notification Inbox state.

**Tech Stack:** Vue 3 Options API, Vue Router, Axios, native EventSource, Node test runner, CSS.

---

## File structure

- `frontend/src/lib/operator-console.js`: lane mapping, drawer action validation, notification reducers.
- `frontend/test/operator-console.test.js`: pure behavior tests.
- `frontend/src/api/index.js`: project Issue fetch and supported Engine decision wrappers.
- `frontend/src/App.vue`: new shell and navigation.
- `frontend/src/router/index.js`: dashboard and project board routes only.
- `frontend/src/components/Dashboard.vue`: project summary and operating queue.
- `frontend/src/components/ProjectBoard.vue`: filters, lanes, cards and project-level data loading.
- `frontend/src/components/IssueDrawer.vue`: issue detail and decision form.
- `frontend/src/components/NotificationInbox.vue`: Inbox state and SSE lifecycle.
- `frontend/src/assets/app.css`: new application visual system.

### Task 1: Define tested board and notification behavior

**Files:**
- Create: `frontend/src/lib/operator-console.js`
- Create: `frontend/test/operator-console.test.js`

- [ ] **Step 1: Write failing behavior tests**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { groupIssuesByLane, mergeNotification, validateDecision } from '../src/lib/operator-console.js'

test('places each issue in one operator lane', () => {
  const groups = groupIssuesByLane([
    { id: '1', status: 'OPEN' },
    { id: '2', status: 'IN_PROGRESS' },
    { id: '3', status: 'IN_REVIEW' },
    { id: '4', status: 'DONE' },
    { id: '5', status: 'FAILED' },
  ])
  assert.deepEqual(Object.values(groups).map((items) => items.length), [0, 2, 0, 1, 1, 1])
})

test('deduplicates replayed SSE notifications by event id', () => {
  const first = mergeNotification([], { eventId: 'evt-1', notificationId: 'n-1', readAt: null })
  assert.equal(mergeNotification(first, { eventId: 'evt-1', notificationId: 'n-1', readAt: null }).length, 1)
})

test('requires a target stage for rejection', () => {
  assert.equal(validateDecision('REJECT', '', '').error, '반려 대상 단계를 선택하세요.')
})
```

- [ ] **Step 2: Run tests and verify RED**

Run: `npm test -- test/operator-console.test.js`

Expected: FAIL because `operator-console.js` does not exist.

- [ ] **Step 3: Add only the tested pure functions**

```js
export const operatorLanes = [
  { id: 'approval', label: '승인 대기' }, { id: 'development', label: '개발 중' },
  { id: 'qa', label: 'QA' }, { id: 'review', label: '리뷰·병합' },
  { id: 'revision', label: '수정 필요' }, { id: 'done', label: '완료' },
]

export function groupIssuesByLane(issues) {
  return issues.reduce((groups, issue) => {
    const lane = ['DONE', 'CANCELLED'].includes(issue.status) ? 'done'
      : ['FAILED', 'PAUSED', 'REJECTED'].includes(issue.status) ? 'revision'
      : issue.status === 'IN_REVIEW' ? 'review'
      : issue.workflowStage === 'QA' ? 'qa'
      : 'development'
    groups[lane].push(issue); return groups
  }, Object.fromEntries(operatorLanes.map((lane) => [lane.id, []])))
}

export function mergeNotification(items, notification) {
  return items.some((item) => item.eventId === notification.eventId) ? items : [notification, ...items]
}

export function validateDecision(decision, reason, targetStage) {
  if (decision === 'REJECT' && !targetStage) return { error: '반려 대상 단계를 선택하세요.' }
  if (['REJECT', 'REQUEST_REVISION'].includes(decision) && !reason.trim()) return { error: '사유를 입력하세요.' }
  return { error: null }
}
```

- [ ] **Step 4: Run the focused and complete test suite**

Run: `npm test`

Expected: all tests pass; `OPEN` and `IN_PROGRESS` issues map to `development`, while `approval` remains empty until an explicit approval-wait state is available.

### Task 2: Replace shell, routes and API adapter

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/api/index.js`

- [ ] **Step 1: Route dashboard and project board**

```js
import Dashboard from '../components/Dashboard.vue'
import ProjectBoard from '../components/ProjectBoard.vue'

export default createRouter({ history: createWebHashHistory(), routes: [
  { path: '/', component: Dashboard },
  { path: '/projects/:projectId/board', component: ProjectBoard, props: true },
] })
```

- [ ] **Step 2: Retain only supported API calls**

```js
export const ProjectApi = {
  list: () => http.get('/projects'),
  issues: (projectId) => http.get(`/projects/${projectId}/issues`),
  notificationList: (projectId) => http.get(`/projects/${projectId}/notifications`),
  unreadCount: (projectId) => http.get(`/projects/${projectId}/notifications/unread-count`),
}
export const WorkflowApi = {
  get: (workflowRunId) => http.get(`/engine/workflow-runs/${workflowRunId}`),
  attempts: (workflowRunId) => http.get(`/engine/workflow-runs/${workflowRunId}/attempts`),
  decide: (workflowRunId, body) => http.post(`/engine/workflow-runs/${workflowRunId}/decisions`, body),
}
```

- [ ] **Step 3: Render the new app shell**

```vue
<aside class="app-sidebar"><router-link to="/">Agentic Worker</router-link><router-link to="/">대시보드</router-link></aside>
<main class="app-main"><router-view /></main>
```

- [ ] **Step 4: Build**

Run: `npm run build`

Expected: Vite build succeeds.

### Task 3: Implement Dashboard and Project Kanban board

**Files:**
- Create: `frontend/src/components/Dashboard.vue`
- Create: `frontend/src/components/ProjectBoard.vue`
- Modify: `frontend/src/assets/app.css`

- [ ] **Step 1: Load and render dashboard projects**

```vue
<article v-for="project in projects" :key="project.id" class="project-card" @click="$router.push(`/projects/${project.id}/board`)">
  <strong>{{ project.name }}</strong><small>{{ project.repositoryUri }}</small>
</article>
```

Use `ProjectApi.list()` in `mounted`; show a retry button only when it rejects.

- [ ] **Step 2: Load project issues and render six lanes**

```vue
<section v-for="lane in operatorLanes" :key="lane.id" class="board-lane">
  <header><h2>{{ lane.label }}</h2><span>{{ laneIssues[lane.id].length }}</span></header>
  <button v-for="issue in laneIssues[lane.id]" :key="issue.id" class="issue-card" @click="selectedIssue = issue">
    <small>#{{ issue.issueNumber }}</small><strong>{{ issue.title }}</strong><span>{{ issue.priority }}</span>
  </button>
</section>
```

Use `ProjectApi.issues(projectId)` in `mounted` and compute `laneIssues` with `groupIssuesByLane(filteredIssues)`.

- [ ] **Step 3: Add desktop and mobile board styles**

```css
.board { display: grid; grid-template-columns: repeat(6, minmax(248px, 1fr)); gap: 16px; overflow-x: auto; }
.board-lane { min-height: 460px; padding: 14px; border-radius: 14px; background: var(--surface-muted); }
.issue-card { display: grid; gap: 8px; width: 100%; margin-top: 10px; padding: 14px; text-align: left; background: #fff; border: 1px solid var(--border); border-radius: 10px; }
@media (max-width: 720px) { .app-sidebar { display: none; } .board { grid-template-columns: repeat(6, minmax(276px, 1fr)); } }
```

- [ ] **Step 4: Build**

Run: `npm run build`

Expected: Vite build succeeds.

### Task 4: Implement Issue drawer and decision boundaries

**Files:**
- Create: `frontend/src/components/IssueDrawer.vue`
- Modify: `frontend/src/components/ProjectBoard.vue`

- [ ] **Step 1: Render closeable drawer**

```vue
<IssueDrawer v-if="selectedIssue" :issue="selectedIssue" @close="selectedIssue = null" />
```

- [ ] **Step 2: Disable actions without a workflow run**

```vue
<p v-if="!issue.workflowRunId" class="drawer-note">워크플로 시작 전에는 승인·반려를 처리할 수 없습니다.</p>
<button :disabled="!issue.workflowRunId || submitting" @click="submit('APPROVE')">승인</button>
```

- [ ] **Step 3: Apply pure validation before API call**

```js
const validation = validateDecision(decision, this.reason, this.targetStage)
if (validation.error) { this.error = validation.error; return }
await WorkflowApi.decide(this.issue.workflowRunId, { decision, reason: this.reason || null, targetStage: this.targetStage || null })
```

- [ ] **Step 4: Run tests and build**

Run: `npm test && npm run build`

Expected: all tests pass and Vite build succeeds.

### Task 5: Implement resilient project notification Inbox

**Files:**
- Create: `frontend/src/components/NotificationInbox.vue`
- Modify: `frontend/src/components/ProjectBoard.vue`

- [ ] **Step 1: Fetch Inbox and unread count**

```js
const [notifications, count] = await Promise.all([
  ProjectApi.notificationList(this.projectId), ProjectApi.unreadCount(this.projectId),
])
this.notifications = notifications.data.items
this.unreadCount = count.data.unreadCount
```

- [ ] **Step 2: Subscribe with duplicate-safe handlers**

```js
this.source = new EventSource(`/api/projects/${this.projectId}/notifications/stream`)
this.source.addEventListener('notification.created', (event) => {
  this.notifications = mergeNotification(this.notifications, JSON.parse(event.data))
  this.unreadCount = this.notifications.filter((item) => !item.readAt).length
})
this.source.addEventListener('reset', () => this.loadNotifications())
```

- [ ] **Step 3: Close stream on component teardown**

```js
beforeUnmount() { this.source?.close() }
```

- [ ] **Step 4: Run tests and build**

Run: `npm test && npm run build`

Expected: all tests pass and Vite build succeeds.

### Task 6: Browser verification

**Files:** No source changes expected.

- [ ] **Step 1: Verify dashboard**

Open `http://127.0.0.1:5173/#/`; confirm project cards, empty/error state, and project board navigation.

- [ ] **Step 2: Verify board and drawer**

Open a project board, verify six lane headings, select a card, then close the drawer. Confirm cards and active filters remain visible.

- [ ] **Step 3: Verify mobile**

At 390px, verify board horizontal scroll and a bottom drawer with visible close control.

- [ ] **Step 4: Check console**

Check browser error/warn logs. Report project API availability errors separately from frontend rendering errors.
