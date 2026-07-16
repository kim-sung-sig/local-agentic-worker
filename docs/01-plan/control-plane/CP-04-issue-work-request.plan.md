# CP-04 Issue Core Boundary Implementation Plan

**Goal:** Create and manage Issues for remote Projects without coupling Issue creation or Issue API endpoints to Agent or synchronization components.

**Files:**
- Modify: `src/main/java/com/example/worker/issue/application/service/IssueCommandService.java`
- Modify: `src/main/java/com/example/worker/issue/api/controller/IssueController.java`
- Delete: `src/main/java/com/example/worker/issue/application/port/IssueEventPublisher.java`
- Delete: `src/main/java/com/example/worker/issue/infrastructure/kafka/KafkaIssueEventPublisher.java`
- Delete: `src/main/java/com/example/worker/issue/application/service/IssueReviewService.java`
- Delete: `src/main/java/com/example/worker/issue/api/controller/IssueReviewController.java`
- Test: `src/test/java/com/example/worker/issue/application/service/IssueCommandServiceTest.java`

## Acceptance criteria

- Creating an Issue for a remote Project saves the Issue without a local checkout, Agent, or Kafka collaborator.
- Issue creation, retrieval, listing, and status update remain available through Issue application services and API endpoints.
- The Issue controller has no `agent` package import and exposes no `/agent-job` endpoint.
- The Issue command service has no Kafka/event-publisher dependency.
- Agent-specific Issue review service and endpoints are absent from the Control Plane core.

## TDD steps

- [ ] Add an Issue-command test asserting remote Issue creation saves an Issue without event publisher setup.
- [ ] Run the focused test; expect failure before the command boundary change.
- [ ] Add `CreateIssueCommand`; change IssueCommandService and controller to use it.
- [ ] Remove Issue event publishing and Agent-job API coupling from the Issue core.
- [ ] Run focused Issue tests and `git diff --check`; scan Issue core imports for Agent/Kafka/Temporal types.
- [ ] Commit only CP-04 files: `git commit -m "feat: isolate issue core"`.

## Test method

Mockito unit tests for Issue command behavior. CP-05 performs the remote Project-to-Issue integration verification.
