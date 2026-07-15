# T01 — Temporal Foundation

**Depends on:** none  
**Goal:** Run a Java Temporal worker from Spring Boot without changing the current Agent execution path.

## Files

- Modify: `build.gradle` — add pinned Temporal SDK and Spring integration dependencies.
- Create: `src/main/java/com/example/worker/engine/infrastructure/temporal/TemporalConfiguration.java`
- Create: `src/main/java/com/example/worker/engine/workflow/EngineHealthWorkflow.java`
- Create: `src/main/java/com/example/worker/engine/workflow/EngineHealthWorkflowImpl.java`
- Create: `src/test/java/com/example/worker/engine/workflow/EngineHealthWorkflowTest.java`
- Modify: `src/main/resources/application.properties` — Temporal endpoint, namespace, and task queue configuration.

## Implementation steps

- [ ] Add an explicit Temporal SDK version; do not use a floating `1.+` version.
- [ ] Define the smallest deterministic health workflow:

```java
@WorkflowInterface
public interface EngineHealthWorkflow {
    @WorkflowMethod String run();
}
```

- [ ] Register the workflow in the configured `agent-worker-engine` task queue.
- [ ] Add separate `temporal.*` properties for local development; do not reuse Kafka configuration.

## Success criteria

- Spring Boot creates the Temporal client and worker with the configured namespace and queue.
- `EngineHealthWorkflow.run()` returns `"ok"` through Temporal.
- No Git, Claude, file, or database I/O is present in a Workflow implementation.

## Test method

- Unit test the workflow with `TestWorkflowEnvironment` and assert `run()` returns `"ok"`.
- Start the application against a local Temporal development server and verify the worker registers once.

## Quality gate and review

- Run: `./gradlew.bat test --tests "*EngineHealthWorkflowTest"`.
- Run: `./gradlew.bat check`.
- Review: confirm SDK versions are pinned and the workflow contains only deterministic code.

## Handoff record

- Record dependency versions, Temporal endpoint, task queue name, test output, and reviewer decision in the Task PR.
