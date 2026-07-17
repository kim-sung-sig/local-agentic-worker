# Agent Team Harness Design

## Goal

Configure a reusable agent team for this repository: exploration, backend and frontend implementation, independent backend and frontend review, and orchestration.

## Roles

| Agent | Access | Responsibility |
|---|---|---|
| `code-explorer` | read-only | Locate affected modules, contracts, conventions, and existing tests. |
| `backend-implementer` | write | Implement backend changes and focused tests following DDD layers. |
| `frontend-implementer` | write | Implement screen changes and focused frontend tests. |
| `backend-reviewer` | read-only | Review backend diffs for layering, contracts, errors, and test gaps. |
| `frontend-reviewer` | read-only | Review UI diffs for behavior, accessibility basics, and API contract alignment. |
| `orchestrator` | coordination | Assign work, sequence exploration, parallel implementation and review, and request only necessary corrections. |

## Workflow

1. The orchestrator asks `code-explorer` for the impacted scope.
2. It dispatches backend and frontend implementation only when that scope requires them; independent work runs in parallel.
3. After implementation, it dispatches the corresponding read-only reviewers in parallel.
4. It sends actionable findings to the owning implementer for the smallest correction, then reports verification status.

## Boundaries

- The team uses role definitions in `.Codex/agents/` and one reusable orchestrator skill in `.Codex/skills/agent-team-orchestrator/`.
- No dedicated skill is added for every role: project conventions and existing skills already provide the execution guidance.
- Existing working-tree changes are preserved. The harness never commits, pushes, changes project configuration, or deletes files.

## Verification

Validate that all six agent definitions exist, the orchestrator references each agent, YAML frontmatter is valid, and the AGENTS.md pointer matches the actual trigger skill.
