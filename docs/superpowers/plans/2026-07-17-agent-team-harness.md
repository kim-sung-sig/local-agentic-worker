# Agent Team Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reusable role definitions and one orchestration skill for backend/frontend development and review.

**Architecture:** Six Markdown agent definitions separate discovery, write-capable implementation, read-only review, and coordination. A single orchestration skill selects only the needed specialists, captures findings through task messages, and requests the smallest corrective change.

**Tech Stack:** Codex agent definitions, Markdown skills, existing Java/Spring and Vue project conventions.

---

### Task 1: Add role definitions

**Files:**
- Create: `.Codex/agents/code-explorer.md`
- Create: `.Codex/agents/backend-implementer.md`
- Create: `.Codex/agents/frontend-implementer.md`
- Create: `.Codex/agents/backend-reviewer.md`
- Create: `.Codex/agents/frontend-reviewer.md`
- Create: `.Codex/agents/orchestrator.md`

- [ ] **Step 1: Define the six roles**

Each definition contains these sections: `핵심 역할`, `작업 원칙`, `입력/출력 프로토콜`, `에러 핸들링`, and `협업`.

- [ ] **Step 2: Apply boundaries**

Mark the explorer and reviewers as read-only; allow only implementers to modify code and focused tests. Require backend work to preserve `domain → application → api` layering and frontend work to preserve existing Vue patterns.

- [ ] **Step 3: Verify files**

Run: `Get-ChildItem .Codex/agents -File | Select-Object -Expand Name`

Expected: six role-definition filenames.

### Task 2: Add the orchestrator skill

**Files:**
- Create: `.Codex/skills/agent-team-orchestrator/SKILL.md`

- [ ] **Step 1: Define triggers and execution mode**

Add YAML frontmatter that triggers for feature implementation, backend/frontend changes, reviews, and reruns. Declare agent-team mode and `model: opus` for dispatched agents.

- [ ] **Step 2: Define the workflow**

Specify: inspect existing artifacts; explore scope; dispatch only relevant implementers in parallel; run matching reviews in parallel; send findings to the owning implementer; report verification. Record that a failed specialist is retried once, then reported as missing without blocking unrelated work.

- [ ] **Step 3: Include test scenarios**

Document one normal flow (combined API and screen change) and one error flow (reviewer unavailable), so later users can inspect the collaboration contract.

- [ ] **Step 4: Verify references**

Run: `Select-String -Path .Codex/skills/agent-team-orchestrator/SKILL.md -Pattern 'code-explorer|backend-implementer|frontend-implementer|backend-reviewer|frontend-reviewer|orchestrator'`

Expected: all six role names are present.

### Task 3: Register the harness pointer

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Add a minimal harness pointer**

Append an `## 하네스: 개발 에이전트 팀` section with the trigger: use `agent-team-orchestrator` for implementation or review work; answer simple questions directly.

- [ ] **Step 2: Add the change-history entry**

Record `2026-07-17`, initial team configuration, all role definitions and the orchestrator skill, and the reason: reusable development/review coordination.

- [ ] **Step 3: Verify consistency**

Run: `Select-String -Path AGENTS.md -Pattern 'agent-team-orchestrator'`

Expected: one harness trigger entry.

### Task 4: Run structural verification

**Files:**
- Verify: `.Codex/agents/*.md`
- Verify: `.Codex/skills/agent-team-orchestrator/SKILL.md`
- Verify: `AGENTS.md`

- [ ] **Step 1: Check required sections**

Run: `Select-String -Path .Codex/agents/*.md -Pattern '핵심 역할|작업 원칙|입력/출력 프로토콜|에러 핸들링|협업'`

Expected: each definition contains all five sections.

- [ ] **Step 2: Check skill frontmatter and references**

Run: `Get-Content .Codex/skills/agent-team-orchestrator/SKILL.md -TotalCount 12`

Expected: `name` and `description` frontmatter fields, then workflow text referencing all roles.

- [ ] **Step 3: Inspect the final diff**

Run: `git diff -- AGENTS.md .Codex docs/superpowers`

Expected: only the harness pointer, agent definitions, orchestration skill, and planning documents.
