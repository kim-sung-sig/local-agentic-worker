import { GenericContainer, Wait, type StartedTestContainer } from 'testcontainers'
import { drizzle } from 'drizzle-orm/node-postgres'
import { Pool } from 'pg'
import { controlPlane } from '@agentic-worker/db'

export interface TestDatabase {
  container: StartedTestContainer
  pool: Pool
  db: ReturnType<typeof drizzle<typeof controlPlane>>
}

/**
 * Starts a `postgres:16-alpine` testcontainer with a proper readiness wait (the
 * "database system is ready to accept connections" log line appears twice - once for
 * initdb's throwaway startup, once for the real server - so we wait for both) instead of
 * relying on the mapped port alone, which can be accepted by the OS before Postgres is
 * ready to serve queries. Sets `process.env.DATABASE_URL` so `getDb()` (server/utils/db.ts)
 * resolves to this container.
 */
export async function startTestDatabase(): Promise<TestDatabase> {
  const container = await new GenericContainer('postgres:16-alpine')
    .withEnvironment({ POSTGRES_PASSWORD: 'test', POSTGRES_DB: 'test' })
    .withExposedPorts(5432)
    .withWaitStrategy(Wait.forLogMessage(/database system is ready to accept connections/, 2))
    .start()

  const host = container.getHost()
  const port = container.getMappedPort(5432)
  const pool = new Pool({ host, port, user: 'postgres', password: 'test', database: 'test' })

  // Belt-and-suspenders: retry the first real query in case the driver connects during
  // a brief window before Postgres is fully accepting connections.
  await retryConnect(pool)

  const db = drizzle(pool, { schema: controlPlane })
  process.env.DATABASE_URL = `postgresql://postgres:test@${host}:${port}/test`

  return { container, pool, db }
}

export async function stopTestDatabase(testDb: TestDatabase): Promise<void> {
  await testDb.pool.end()
  await testDb.container.stop()
}

async function retryConnect(pool: Pool, attempts = 10, delayMs = 300): Promise<void> {
  let lastError: unknown
  for (let attempt = 0; attempt < attempts; attempt++) {
    try {
      await pool.query('SELECT 1')
      return
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, delayMs))
    }
  }
  throw lastError
}
