# Workflow Kanban Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the table-based workflow console with a five-lane Kanban board, detail drawer, and project notification Inbox.

**Architecture:** Keep the existing Vue Options API and mock Workflow Run source. Add pure lane grouping helpers in the existing workflow-console module; render lanes/cards and a conditional drawer in the existing component. Consume the implemented project notification SSE only when a project ID is available, and retain a non-SSE fallback.

**Tech Stack:** Vue 3, Vue Router, Axios/EventSource, Node test runner, CSS.

---

## File structure

- `frontend/src/lib/workflow-console.js`: lane metadata, pure grouping/filtering helpers, mock notification shape.
- `frontend/test/workflow-console.test.js`: lane assignment and filtering checks.
- `frontend/src/components/WorkflowConsole.vue`: Kanban board, drawer, Inbox, lifecycle-managed EventSource.
- `frontend/src/assets/app.css`: board, card, drawer, Inbox, responsive styles.

### Task 1: Add deterministic board lane helpers

**Files:**
- Modify: `frontend/src/lib/workflow-console.js`
- Modify: `frontend/test/workflow-console.test.js`

- [x] **Step 1: Write failing lane assignment tests**

```js
import { filterRuns, groupRunsByLane } from '../src/lib/workflow-console.js'

test('groups runs into the five operator lanes', () => {
  const groups = groupRunsByLane([
    { currentStage: 'PLANNING', status: 'RUNNING' },
    { currentStage: 'IMPLEMENTATION', status: 'RUNNING' },
    { currentStage: 'QA', status: 'PAUSED' },
    { currentStage: 'REVIEW_MERGE', status: 'RUNNING' },
    { currentStage: 'QA', status: 'FAILED' },
  ])
  assert.equal(groups.approval.length, 1)
  assert.equal(groups.automatic.length, 1)
  assert.equal(groups.qa.length, 1)
  assert.equal(groups.review.length, 1)
  assert.equal(groups.completed.length, 1)
})
```

- [x] **Step 2: Run the focused test and confirm failure**

Run: `npm test -- test/workflow-console.test.js`

Expected: FAIL because `groupRunsByLane` is not exported.

- [x] **Step 3: Add the minimal lane map and helper**

```js
export const workflowLanes = [
  { id: 'approval', label: '승인 대기' },
  { id: 'automatic', label: '자동 실행' },
  { id: 'qa', label: 'QA' },
  { id: 'review', label: '검토·병합' },
  { id: 'completed', label: '종료' },
]

export function groupRunsByLane(runs) {
  return runs.reduce((groups, run) => {
    const lane = ['COMPLETED', 'FAILED', 'CANCELLED'].includes(run.status) ? 'completed'
      : ['INTAKE', 'PLANNING'].includes(run.currentStage) ? 'approval'
      : ['WORKSPACE', 'IMPLEMENTATION'].includes(run.currentStage) ? 'automatic'
      : run.currentStage === 'QA' ? 'qa' : 'review'
    groups[lane].push(run)
    return groups
  }, { approval: [], automatic: [], qa: [], review: [], completed: [] })
}
```

- [x] **Step 4: Run all frontend unit tests**

Run: `npm test`

Expected: all tests pass.

### Task 2: Replace table list with Kanban board and drawer

**Files:**
- Modify: `frontend/src/components/WorkflowConsole.vue`
- Modify: `frontend/src/assets/app.css`

- [x] **Step 1: Render lanes from the pure helper**

```vue
<section class="kanban-board" aria-label="Workflow Run 칸반 보드">
  <section v-for="lane in workflowLanes" :key="lane.id" class="kanban-lane">
    <header><h3>{{ lane.label }}</h3><span>{{ laneRuns[lane.id].length }}</span></header>
    <button v-for="run in laneRuns[lane.id]" :key="run.workflowRunId" class="workflow-card" @click="selectedRunId = run.workflowRunId">
      <strong>{{ run.ticketId }}</strong>
      <span>{{ stageLabel(run.currentStage) }}</span>
      <small>QA {{ latestAttempt(run)?.qaScore ?? '-' }} · {{ run.attempts.length }}/{{ maxAttempts }}</small>
    </button>
  </section>
</section>
```

- [x] **Step 2: Make the existing detail area a closeable drawer**

```vue
<aside v-if="selectedRun" class="workflow-drawer" aria-label="선택한 Workflow Run 상세">
  <button class="drawer-close" aria-label="상세 닫기" @click="selectedRunId = null">×</button>
  <!-- retain timeline, action form, and attempt table -->
</aside>
```

- [x] **Step 3: Add the smallest board and responsive CSS**

```css
.kanban-board { display: grid; grid-template-columns: repeat(5, minmax(240px, 1fr)); gap: 16px; overflow-x: auto; }
.kanban-lane { min-height: 420px; padding: 12px; background: #f1f5f9; border-radius: 10px; }
.workflow-card { display: grid; gap: 8px; width: 100%; margin-top: 10px; padding: 14px; text-align: left; background: #fff; border: 1px solid #dbe3ef; border-radius: 8px; cursor: pointer; }
.workflow-drawer { position: fixed; inset: 52px 0 0 auto; width: min(440px, 100%); overflow-y: auto; background: #fff; box-shadow: -8px 0 24px rgba(15, 23, 42, .16); }
@media (max-width: 900px) { .workflow-drawer { inset: auto 0 0; width: 100%; max-height: 75vh; } }
```

- [x] **Step 4: Run build**

Run: `npm run build`

Expected: Vite build succeeds.

### Task 3: Add notification Inbox with safe SSE fallback

**Files:**
- Modify: `frontend/src/components/WorkflowConsole.vue`

- [x] **Step 1: Render a compact Inbox panel and unread badge**

```vue
<button class="notification-toggle" @click="notificationOpen = !notificationOpen">알림 {{ unreadCount }}</button>
<aside v-if="notificationOpen" class="notification-inbox" aria-label="알림 Inbox">
  <p v-if="notifications.length === 0">새 알림이 없습니다.</p>
  <article v-for="notification in notifications" :key="notification.notificationId">
    <strong>{{ notification.title }}</strong><p>{{ notification.message }}</p>
  </article>
</aside>
```

- [x] **Step 2: Open the implemented SSE endpoint only with a selected project**

```js
mounted() {
  if (!this.projectId) return
  this.notificationSource = new EventSource(`/api/projects/${this.projectId}/notifications/stream`)
  this.notificationSource.addEventListener('notification.created', (event) => {
    const notification = JSON.parse(event.data)
    this.notifications.unshift(notification)
    this.unreadCount += notification.readAt ? 0 : 1
  })
},
beforeUnmount() {
  this.notificationSource?.close()
},
```

- [x] **Step 3: Keep board use independent of SSE failure**

Handle `reset` by clearing only the local Inbox list and show a reconnect-neutral empty state. Do not modify the Kanban card data from notification events.

- [x] **Step 4: Run tests and build**

Run: `npm test && npm run build`

Expected: all unit tests and build pass.

### Task 4: Browser verification

**Files:**
- No source changes expected.

- [x] **Step 1: Verify desktop layout**

Open `http://127.0.0.1:5173/#/workflow-runs`; confirm five lane headers, at least one card, drawer close button, and no framework error overlay.

- [x] **Step 2: Verify interaction**

Click one card; confirm its detail drawer opens. Click `상세 닫기`; confirm it closes while cards remain visible.

- [x] **Step 3: Verify mobile layout**

At 390px viewport, confirm board lanes remain horizontally scrollable and the drawer opens as a bottom panel without clipping.

- [x] **Step 4: Check console health**

Inspect browser warnings/errors after loading. Record pre-existing unrelated project-list errors separately from Kanban errors.
