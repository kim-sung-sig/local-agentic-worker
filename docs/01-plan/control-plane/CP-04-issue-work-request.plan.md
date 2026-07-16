# CP-04 Issue Work Request Implementation Plan

**Goal:** Save exactly one `WorkRequested` delivery record when an Issue is created and publish it asynchronously without coupling Issue creation to Temporal.

**Files:**
- Create: `src/main/resources/db/migration/V7__add_work_request_outbox.sql`
- Create: `src/main/java/com/example/worker/issue/application/port/WorkRequestPublisher.java`
- Create: `src/main/java/com/example/worker/issue/domain/model/WorkRequestOutboxEntry.java`
- Create: `src/main/java/com/example/worker/issue/application/port/WorkRequestOutboxRepository.java`
- Create: `src/main/java/com/example/worker/issue/infrastructure/datasource/WorkRequestOutboxJpaEntity.java`
- Create: `src/main/java/com/example/worker/issue/infrastructure/datasource/WorkRequestOutboxRepositoryAdapter.java`
- Create: `src/main/java/com/example/worker/issue/infrastructure/kafka/KafkaWorkRequestPublisher.java`
- Create: `src/main/java/com/example/worker/issue/application/service/WorkRequestDispatcher.java`
- Modify: `src/main/java/com/example/worker/issue/application/service/IssueCommandService.java`
- Modify: `src/main/java/com/example/worker/contracts/work/WorkRequested.java` only if CP-04 tests reveal a missing contract field
- Test: `src/test/java/com/example/worker/issue/application/service/IssueCommandServiceTest.java`
- Test: `src/test/java/com/example/worker/issue/application/service/WorkRequestDispatcherTest.java`

## Acceptance criteria

- Creating an Issue for a remote Project saves an Issue and one pending outbox entry in the same transaction.
- The message is built from the saved Issue and remote Project: issue ID, project ID, repository URI, base branch, raw specification, timestamp.
- The request uses `WorkRequested.workflowId()` for deterministic downstream idempotency.
- A broker failure leaves the entry pending with failure information; a later dispatcher retry can publish it.
- No `projectLocalPath` appears in the new message path.

## TDD steps

- [ ] Add an Issue-command test asserting remote Issue creation saves one pending outbox entry and no longer publishes the legacy local-path event for that Project.
- [ ] Run the focused test; expect failure before outbox support.
- [ ] Add the outbox domain model, repository port, and V7 table with a unique `issue_id` constraint.
- [ ] Update `IssueCommandService` to save the outbox entry inside its existing transaction.
- [ ] Add a dispatcher test: a pending entry is published through `WorkRequestPublisher` and marked delivered only after success.
- [ ] Implement the JPA adapter, Kafka publisher, and dispatcher with a retryable pending state on publish failure.
- [ ] Run both focused tests; expect success.
- [ ] Run `git diff --check`; review that contracts contain no secret/local-path/framework imports.
- [ ] Commit only CP-04 files: `git commit -m "feat: publish issue work request"`.

## Test method

Mockito unit tests for transaction collaborators and dispatcher state. CP-05 performs the actual database migration and broker-facing boundary verification.
