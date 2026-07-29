---
name: agent-team-orchestrator
description: "Use for backend implementation, frontend/screen implementation, code exploration, backend review, frontend review, or re-running and improving those tasks."
---

# Development Agent Team Orchestration

Read `.agents/agent-team.md` before assigning roles. Use the local `.claude/agents/` definitions.

1. Ask `backend-planner` for scope and task contracts.
2. Dispatch only required `backend-developer` roles; parallelize only independent tasks.
3. Dispatch `backend-reviewer` for spec and quality review.
4. Send actionable findings to the owning developer for the smallest correction and re-run relevant verification.
5. Report changes, verification, and any unresolved or unavailable review.
