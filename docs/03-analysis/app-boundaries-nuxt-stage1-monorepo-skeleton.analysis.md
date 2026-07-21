# [Analysis] Nuxt/Temporal TS Migration — Stage 1: Monorepo Skeleton

**Plan:** `docs/superpowers/plans/2026-07-20-nuxt-control-plane-temporal-migration.md`
**PDCA phase:** Check

| Stage 1 acceptance criterion | Evidence | Result |
|---|---|---|
| `apps/control-plane` (Nuxt) runs independently | `npx nuxt build` and `npx nuxt typecheck` both succeed | Met |
| `apps/temporal-worker` runs independently | `npm run test`/`npm run typecheck` in that workspace pass | Met |
| `packages/contracts` runs independently | `npm run test`/`npm run typecheck` in that workspace pass | Met |
| typecheck | root `npm run typecheck` (delegates to each workspace's `nuxt typecheck`/`tsc --noEmit`) | Met |
| lint | root `npm run lint` (`eslint .`), scoped to `packages/*` and `apps/temporal-worker` | Met |
| Worker smoke test | `apps/temporal-worker/test/smoke.test.ts` proves the TS toolchain and cross-package import from `@agentic-worker/contracts` both work | Met |

## What this task did

- Added a root `package.json` with npm workspaces (`apps/*`, `packages/*`) alongside the existing
  Gradle build — the two build systems are independent; nothing in the Java side changed.
- `packages/contracts`: TypeScript/Zod ports of `WorkRequested` and `EngineNotificationRequested`
  (TDD: tests written first, confirmed failing, then implemented). UUID validation intentionally uses
  a loose 8-4-4-4-12 hex regex rather than `z.string().uuid()`'s strict RFC-version check, to match
  `java.util.UUID.fromString()`'s more permissive acceptance — the Java side does not enforce
  version/variant nibbles either.
- `apps/temporal-worker`: empty scaffold beyond one smoke test; actual Workflow/Activity porting is
  Stage 5 per the migration plan, not this task.
- `apps/control-plane`: minimal Nuxt 4 app (`app.vue` placeholder) proving the Nuxt toolchain boots;
  no Project/Issue/Document API or screens yet (Stage 3/7).
- Lint scope deliberately excludes `apps/control-plane` for now — Vue/Nuxt-aware ESLint config
  (`@nuxt/eslint`) is a separate, slightly heavier setup than plain `typescript-eslint` and wasn't
  needed to prove Stage 1's "independently runnable" bar. Add it when Stage 3 introduces real
  components.
- `.gitignore` updated for `node_modules/`, `.nuxt/`, `.output/`, `*.tsbuildinfo` at the new
  monorepo root (previously only `/frontend/node_modules` was covered).

## Verification evidence

```text
npm run test          # workspaces: temporal-worker (1 test), contracts (5 tests) — all pass
npm run lint           # eslint . — 0 errors
apps/control-plane: npx nuxt build && npx nuxt typecheck  # both succeed
./gradlew.bat build --no-daemon   # BUILD SUCCESSFUL, unaffected by the new npm workspace
```

## Remaining scope (tracked in the migration plan, not this task)

Stages 2-8 (Drizzle schema, Control Plane API/screens, auth, Temporal Workflow/Activity porting,
Worker Gateway with session-affinity routing, notification/webhook wiring, screen migration, and
cutover) are not started. The Java `agent-worker-engine` platform-master-plan AB-03/AB-04 (physical
module split) are paused pending confirmation of which direction (Java multi-module vs. this Nuxt
rewrite) the platform ultimately takes.
