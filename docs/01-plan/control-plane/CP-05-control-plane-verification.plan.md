# CP-05 Control Plane Verification Implementation Plan

**Goal:** Establish the smallest executable verification proving the remote Project-to-work-request Control Plane path.

**Files:**
- Create: `src/test/java/com/example/worker/controlplane/ControlPlaneWorkRequestIntegrationTest.java`
- Modify: `src/test/resources/application-test.properties` only if test-specific database/broker configuration is absent
- Modify: `docs/architecture/control-plane-agent-engine.md`
- Modify: `docs/01-plan/control-plane/control-plane-master-plan.md`

## Acceptance criteria

- The verification registers a remote Project, creates an Issue, and observes one `WorkRequested` with the expected deterministic workflow ID.
- The verification asserts no credential value is exposed in the work request.
- The test isolates its database/broker dependencies using the repository's existing Testcontainers pattern; it does not depend on a developer's local PostgreSQL.
- Documentation records the measured result and does not claim Agent Engine startup or external ticket synchronization.

## TDD steps

- [ ] Locate and reuse the existing Testcontainers conventions in `AgentWorkerEngineIntegrationTest`.
- [ ] Add the failing integration test covering remote Project registration, Issue creation, and captured `WorkRequested` delivery.
- [ ] Run only the new integration test with Docker available; expect failure before the Control Plane flow is fully wired.
- [ ] Add only the test configuration needed to supply PostgreSQL and a replaceable in-memory/captured publisher boundary.
- [ ] Re-run the integration test; expect success.
- [ ] Run all focused Control Plane tests plus `git diff --check`.
- [ ] Update the two named documents with the exact test command and result.
- [ ] Perform a task review: acceptance criteria, no Temporal import in Control Plane, no credential/local path in contract.
- [ ] Commit only CP-05 files: `git commit -m "test: verify control plane work request flow"`.

## Test method

Container-backed integration test for persistence and migration; focused unit tests from CP-01 through CP-04 for domain/application decisions. Legacy Agent failures are reported separately and never masked.
