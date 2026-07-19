# Operator Workflow Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Vue 3 workflow-run console that lets an operator inspect mock workflow runs, filter them, select one, and exercise approval decisions locally.

**Architecture:** Keep all mock data and pure view-state transitions in one JavaScript module so it can be replaced by the documented Engine API later. `WorkflowConsole.vue` composes the table and fixed detail panel; the existing router exposes it at `/workflow-runs`.

**Tech Stack:** Vue 3, Vue Router, native Node.js test runner, Vite.

---

### Task 1: Add mock workflow state with a runnable test

**Files:**
- Create: `frontend/src/lib/workflow-console.js`
- Create: `frontend/test/workflow-console.test.js`
- Modify: `frontend/package.json`

- [ ] **Step 1: Write the failing test**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { filterRuns, retryRun } from '../src/lib/workflow-console.js'

test('filters workflow runs by text and status', () => {
  const runs = [{ workflowRunId: 'run-1', ticketId: 'TKT-2481', status: 'PAUSED' }]
  assert.deepEqual(filterRuns(runs, '2481', 'PAUSED'), runs)
})

test('retries a paused workflow run', () => {
  assert.equal(retryRun({ status: 'PAUSED' }).status, 'RUNNING')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test test/workflow-console.test.js` from `frontend`

Expected: FAIL because `workflow-console.js` does not exist.

- [ ] **Step 3: Write minimal implementation**

```js
export function filterRuns(runs, query, status) {
  const text = query.trim().toLowerCase()
  return runs.filter((run) => (!text || `${run.ticketId} ${run.workflowRunId}`.toLowerCase().includes(text))
    && (!status || run.status === status))
}

export function retryRun(run) {
  return { ...run, status: 'RUNNING' }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test test/workflow-console.test.js` from `frontend`

Expected: PASS with 2 passing tests.

### Task 2: Build the console route and mock interactions

**Files:**
- Create: `frontend/src/components/WorkflowConsole.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/assets/app.css`

- [ ] **Step 1: Write the failing behavior test**

```js
import { applyDecision } from '../src/lib/workflow-console.js'

test('approving review and merge completes the workflow run', () => {
  const result = applyDecision({ currentStage: 'REVIEW_MERGE', status: 'RUNNING' }, 'APPROVE')
  assert.equal(result.status, 'COMPLETED')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test test/workflow-console.test.js` from `frontend`

Expected: FAIL because `applyDecision` is not exported.

- [ ] **Step 3: Implement the minimal interactive screen**

```js
export function applyDecision(run, decision) {
  if (decision === 'RETRY') return retryRun(run)
  if (decision === 'REQUEST_REVISION') return { ...run, status: 'PAUSED' }
  if (decision === 'APPROVE' && run.currentStage === 'REVIEW_MERGE') {
    return { ...run, status: 'COMPLETED' }
  }
  return { ...run, status: 'RUNNING' }
}
```

Add `WorkflowConsole.vue` with a filterable table, fixed detail panel, six-stage timeline, Attempt history, and local decision buttons. Register `{ path: '/workflow-runs', component: WorkflowConsole }`. Add only console-specific CSS to the existing stylesheet.

- [ ] **Step 4: Run tests and build**

Run: `npm test && npm run build` from `frontend`

Expected: PASS with 3 tests and a successful Vite build.

### Task 3: Verify the rendered console

**Files:**
- No source changes expected.

- [ ] **Step 1: Start the Vite server**

Run: `npm run dev -- --host 127.0.0.1` from `frontend`.

- [ ] **Step 2: Inspect the console**

Open `/#/workflow-runs`; verify filtering, selected-run changes, Retry, Revision Request, and Approve interactions.

- [ ] **Step 3: Verify responsive rendering**

Check desktop and mobile widths. The table must scroll horizontally rather than clipping columns; the detail panel must stack below the list on small screens.

## Self-review

- The plan covers list, detail, six stages, attempts, filtering, and local decisions from the approved design.
- The API request remains outside this implementation; `workflow-console.js` is the single replacement boundary.
- No new dependency is required: Node's built-in test runner supplies the TDD check.
