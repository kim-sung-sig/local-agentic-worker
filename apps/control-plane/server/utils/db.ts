import { drizzle, type NodePgDatabase } from 'drizzle-orm/node-postgres'
import { Pool } from 'pg'
import { controlPlane } from '@agentic-worker/db'

let instance: NodePgDatabase<typeof controlPlane> | null = null
let pool: Pool | null = null

/**
 * Reads the same default `nuxt.config.ts` uses for `runtimeConfig.databaseUrl`. Reading
 * `process.env.DATABASE_URL` directly (instead of `useRuntimeConfig()`) keeps this module
 * usable from plain Vitest service tests, which run outside the Nitro runtime and cannot
 * resolve `#imports`.
 */
function resolveDatabaseUrl(): string {
  return process.env.DATABASE_URL || 'postgresql://dev_user:dev_password@localhost:15432/agentic_worker'
}

export function getDb(): NodePgDatabase<typeof controlPlane> {
  if (!instance) {
    pool = new Pool({ connectionString: resolveDatabaseUrl() })
    instance = drizzle(pool, { schema: controlPlane })
  }
  return instance
}

/**
 * Closes the singleton pool and clears the cached instance. Only needed by tests that
 * tear down the underlying database (e.g. stopping a testcontainer) before process exit.
 */
export async function closeDb(): Promise<void> {
  if (pool) {
    await pool.end()
    pool = null
  }
  instance = null
}
