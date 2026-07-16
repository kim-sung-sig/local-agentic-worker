# CP-05 Control Plane Verification Implementation Plan

**Goal:** Establish the smallest executable verification proving all Agent/Sync-independent remote Project and Issue core use cases.

**Files:**
- Create: `src/test/java/com/example/worker/controlplane/ControlPlaneCoreUseCaseTest.java`
- Modify: `docs/01-plan/control-plane/control-plane-master-plan.md`

## Acceptance criteria

- The verification registers and retrieves a remote Project without exposing its credential reference.
- The verification creates, lists, retrieves, and status-updates an Issue for that Project.
- The test uses in-memory repository ports and requires no PostgreSQL, Kafka, Temporal, Agent, or synchronization runtime.
- Documentation records the measured result and explicitly excludes Agent Engine and external ticket synchronization.

## TDD steps

- [ ] Add the failing application integration test covering Project registration, Project retrieval, Issue creation/list/get, and status update.
- [ ] Run only the new integration test; expect failure before the in-memory port fixtures are added.
- [ ] Add in-memory implementations of the Project and Issue repository ports inside the test fixture.
- [ ] Re-run the integration test; expect success.
- [ ] Run all focused Control Plane tests plus `git diff --check`.
- [ ] Update the two named documents with the exact test command and result.
- [ ] Perform a task review: all core use cases, no Agent/Kafka/Temporal import in Control Plane Project/Issue application and API packages.
- [ ] Commit only CP-05 files: `git commit -m "test: verify control plane core use cases"`.

## Test method

Application-service integration test using in-memory ports, plus focused unit tests from CP-01 through CP-04. Persistence migration execution is separately verified by the V6 Flyway review and remains infrastructure-specific.
