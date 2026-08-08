# Control Panel Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the active Vue/Vite control panel with a GitHub-like shell and an accessible issue execution drawer.

**Architecture:** Preserve the existing hash routes and Spring API client. `frontend/src/App.vue` owns sidebar state; `IssueWorkspace.vue` owns `executionDrawerOpen`, distinctly from the existing mobile issue-list `drawerOpen`. The drawer reuses `AgentApi.phases()` and current in-page activity only—there is no log-stream API to call.

**Tech Stack:** Vue 3 Options API, Vue Router 4, Vite 5, Node test runner, CSS.

---

## File structure

- Modify: `frontend/src/App.vue` — global navigation and mobile sidebar state.
- Modify: `frontend/src/components/IssueWorkspace.vue` — issue-detail execution drawer state and markup.
- Modify: `frontend/src/assets/app.css` — shell/sidebar and execution drawer styles.
- Modify: `frontend/test/operator-console.test.js` — focused pure UI-state regression tests.
- Modify: `frontend/src/lib/operator-console.js` — only if a small pure drawer/sidebar state helper is required to make the test real.

### Task 1: Global sidebar contract

**Files:**
- Modify: `frontend/test/operator-console.test.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/assets/app.css`

- [ ] **Step 1: Write the failing navigation-state test**

Add a pure state helper to the test import and assert its intended behavior:

```js
test('closes the mobile navigation after a route is selected', () => {
  assert.equal(closeDrawerAfterRoute(true), false)
  assert.equal(closeDrawerAfterRoute(false), false)
})
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `npm test --prefix frontend`

Expected: FAIL because `closeDrawerAfterRoute` does not exist.

- [ ] **Step 3: Implement the minimum helper and shell behavior**

Export the one-line `closeDrawerAfterRoute` helper from `frontend/src/lib/operator-console.js`. In `App.vue`, add `navigationOpen: false`, bind it to a labelled toggle button and `side-nav` open class, and call it when a navigation link is selected. Keep only canonical routes: dashboard, current project, and current issue. Display future unavailable areas as disabled buttons rather than invented routes.

- [ ] **Step 4: Add GitHub-like responsive CSS**

Append scoped override rules to `app.css` for a neutral, compact sidebar, menu toggle, and mobile backdrop. Do not rewrite the existing stylesheet or remove the pre-existing issue-list drawer rules.

- [ ] **Step 5: Run focused tests and build**

Run: `npm test --prefix frontend`

Expected: PASS.

Run: `npm run build --prefix frontend`

Expected: Vite build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/App.vue frontend/src/assets/app.css frontend/src/lib/operator-console.js frontend/test/operator-console.test.js
git commit -m "feat: restore control panel navigation"
```

### Task 2: Issue execution drawer contract

**Files:**
- Modify: `frontend/test/operator-console.test.js`
- Modify: `frontend/src/lib/operator-console.js`
- Modify: `frontend/src/components/IssueWorkspace.vue`
- Modify: `frontend/src/assets/app.css`

- [ ] **Step 1: Write the failing execution-drawer test**

```js
test('closes an execution drawer only for Escape', () => {
  assert.equal(shouldCloseExecutionDrawer('Escape'), true)
  assert.equal(shouldCloseExecutionDrawer('Enter'), false)
})
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `npm test --prefix frontend`

Expected: FAIL because `shouldCloseExecutionDrawer` does not exist.

- [ ] **Step 3: Implement the minimum helper and drawer**

Export `shouldCloseExecutionDrawer(key) { return key === 'Escape' }`. In `IssueWorkspace.vue`, add `executionDrawerOpen: false` without reusing `drawerOpen`. Add a button that opens the drawer, a backdrop that closes it, and a conditional `role="dialog" aria-modal="true" aria-label="실행 현황"` panel. Register a keydown listener in `mounted` and remove it in `beforeUnmount`; close only through the helper and return focus to the opener.

Keep the existing `AgentApi.phases()` and `activity` display inside the drawer. Label current local state as mock where the API cannot supply a real execution log. Do not add an endpoint.

- [ ] **Step 4: Add drawer CSS**

Append `.execution-drawer-backdrop` and `.execution-drawer` rules. The drawer is fixed on the right, scrollable, full-width on screens at or below 800px, and stacked above the existing issue-list drawer without affecting its `drawerOpen` behavior.

- [ ] **Step 5: Run focused tests and build**

Run: `npm test --prefix frontend`

Expected: PASS.

Run: `npm run build --prefix frontend`

Expected: Vite build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/IssueWorkspace.vue frontend/src/assets/app.css frontend/src/lib/operator-console.js frontend/test/operator-console.test.js
git commit -m "feat: add issue execution drawer"
```

### Task 3: Final verification

**Files:**
- Modify: no product files expected

- [ ] **Step 1: Run the frontend suite**

Run: `npm test --prefix frontend`

Expected: PASS.

- [ ] **Step 2: Build the active frontend**

Run: `npm run build --prefix frontend`

Expected: PASS.

- [ ] **Step 3: Check final scope**

Run: `git diff --check` and `git status --short --untracked-files=all`

Expected: no whitespace errors; only intended frontend files and existing unrelated changes are unstaged.
