import { defineConfig } from 'vitest/config'

/**
 * Runs test files sequentially. Several suites here boot their own `postgres:16-alpine`
 * testcontainer and the notification-stream suite also boots a full Nitro dev server -
 * running them in parallel forks contends for Docker/port resources on Windows and causes
 * intermittent "the database system is starting up" failures. Sequential execution is slower
 * but reliable; these are integration tests, not unit tests, so the tradeoff is acceptable.
 */
export default defineConfig({
  test: {
    fileParallelism: false,
  },
})
