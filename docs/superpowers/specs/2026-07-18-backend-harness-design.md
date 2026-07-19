# Backend Harness Design

## Goal

Provide a shared Claude/Codex skill that creates and runs a backend-focused agent team for Spring Boot changes, with explicit specification compliance and production-readiness review.

## Location and trigger

Create the canonical skill at `.agents/skills/backend-harness/SKILL.md`. Mirror it to `.claude/skills/` and `.codex/skills/` so both tools can discover the same workflow.

Trigger for backend feature work, backend review, Spring Boot/JPA/API changes, and requests to build or rerun a backend agent harness.

## Team

| Role | Responsibility | Access |
|---|---|---|
| `backend-implementer` | Implement scoped backend code and focused tests. | write |
| `spec-guardian` | Compare requirements/SDD, API contracts, behavior, and tests against the change. | read-only |
| `quality-security-reviewer` | Review correctness, security boundaries, SRP, maintainability, and necessary comments. | read-only |
| `reliability-reviewer` | Review transactions, concurrency, data volume, hot paths, logs, metrics, and operational behavior when relevant. | read-only |
| `orchestrator` | Explore scope, dispatch only needed roles, collect findings, and request minimal corrections. | coordination |

## Workflow

1. Read the task, current SDD or requirements, contracts, and changed paths.
2. Implement through `backend-implementer`.
3. Run `spec-guardian` and `quality-security-reviewer` for every backend implementation.
4. Run `reliability-reviewer` only when the diff affects persistence, transactions, concurrency, asynchronous work, retry loops, bulk processing, hot endpoints, logging, or metrics.
5. Route actionable findings to the implementer, verify corrected behavior, then report unresolved risks explicitly.

## Review contract

- Every finding must include severity, evidence, impact, and the smallest recommended correction.
- `spec-guardian` blocks completion for behavior outside the approved spec or missing acceptance coverage.
- `quality-security-reviewer` checks trust boundaries, validation, authorization, secrets, injection risks, and whether classes retain one responsibility.
- `reliability-reviewer` checks transactional boundaries, isolation/idempotency, query cardinality, failure/retry behavior, structured logs, metrics, and alertable failure modes.
- Reviewers do not request speculative abstractions, comment-only changes, or unrelated refactoring.

## Verification

Validate frontmatter, the shared/Claude/Codex copies, role references, conditional reliability gates, and an example prompt for API creation and a transactional bulk-operation change.
