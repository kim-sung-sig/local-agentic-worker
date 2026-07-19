---
name: agent-team-orchestrator
description: "Use for backend implementation, frontend/screen implementation, code exploration, backend review, frontend review, or re-running and improving those tasks."
---

# Development Agent Team Orchestration

Read `.agents/agent-team.md` before assigning roles. Use the local `.claude/agents/` definitions.

1. Ask `code-explorer` for scope and contracts.
2. Dispatch only required implementers; run independent backend and frontend work in parallel.
3. Dispatch matching read-only reviewers in parallel.
4. Send actionable findings to the owning implementer for the smallest correction and re-run relevant verification.
5. Report changes, verification, and any unresolved or unavailable review.
