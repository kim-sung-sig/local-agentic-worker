# Control Plane Navigation and Execution Drawer

## Goal

Restore the active Vue/Vite operator console as a GitHub-like control panel: a global sidebar, project and issue workspaces reached by URL, and a non-destructive execution drawer for run context.

## Confirmed interaction model

| Surface | Purpose | Route/state |
| --- | --- | --- |
| Global sidebar | Move between overview, projects, personal work, runs, approval requests, notifications, and settings. | Persistent app shell; collapses on narrow screens. |
| Project page | Own a project's issues, board, documents, and activity. | `/projects/:projectId` |
| Issue page | Main work surface for description, artifacts, discussion, and approval actions. | `/projects/:projectId/issues/:issueId` |
| Execution drawer | Shows run attempts, phase state, recent events/logs, and contextual actions without leaving the issue. | Local UI state on the issue page; keyboard and backdrop closable. |

The drawer is intentionally not a substitute for an issue route. It cannot own canonical issue content or links.

## Scope

- Replace the current one-item navigation with the confirmed sidebar structure.
- Improve the shell, dashboard, project page, and issue workspace visual hierarchy using existing Vue/CSS only.
- Add an accessible issue execution drawer with phase history and the existing client-side worker mock data.
- Preserve existing authentication, API calls, route shapes, and recoverable error states.
- Add focused UI tests for the sidebar and drawer behavior.

## Non-goals

- No new backend endpoints, worker/Temporal calls, packages, or database changes.
- No real-time log streaming. The existing mock worker timeline remains the source for execution data.
- No change to authorization or existing issue/project API contracts.

## Components and state

`frontend/src/App.vue` remains the global shell. It owns sidebar collapse state only. Each hash route continues to own its API loading/error state.

`frontend/src/components/IssueWorkspace.vue` owns `executionDrawerOpen`, separately from its existing mobile issue-list `drawerOpen`. Opening the drawer displays the existing agent phase data and current activity state. Closing uses the close button, Escape, or the backdrop; focus returns to the triggering button. The drawer is marked as a dialog and has an accessible label.

## Responsive behavior

Desktop keeps a compact fixed sidebar. At the existing narrow breakpoint, the sidebar becomes a toggleable overlay; hash-route content remains full width. The execution drawer is full-width on narrow screens and right-aligned on larger screens.

## Acceptance checks

1. Authenticated users can navigate through the restored sidebar without breaking current routes.
2. An issue page can open and close the execution drawer with mouse and keyboard, while keeping the detail page in place.
3. Existing Vite operator-console tests and production build pass.
