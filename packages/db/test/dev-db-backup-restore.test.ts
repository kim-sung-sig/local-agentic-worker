import { execFileSync } from 'node:child_process'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { Client } from 'pg'

// Source dev DB — Java Flyway schema (public), pre-existing container `postgres-source` (postgres:17.6).
const SOURCE_HOST = 'localhost'
const SOURCE_PORT = 15432
const SOURCE_USER = 'dev_user'
const SOURCE_PASSWORD = 'dev_password'
const SOURCE_DB = 'agentic_worker'

// pg_dump/psql must be a v17 client to match the v17.6 source server (a v16 client errors with
// "server version mismatch"). Reached from inside a container via host.docker.internal.
const CLIENT_IMAGE = 'postgres:17-alpine'
const RESTORE_IMAGE = 'postgres:17-alpine'
const RESTORE_USER = 'restore_test'
const RESTORE_PASSWORD = 'restore_test'
const RESTORE_DB = 'restore_test'

const EXPECTED_PUBLIC_TABLES = [
  'agent_job',
  'engine_attempt_record',
  'engine_stage_gate',
  'engine_workflow_run',
  'flyway_schema_history',
  'issue',
  'project',
  'project_notification',
].sort()

function dockerAvailable(): boolean {
  try {
    execFileSync('docker', ['info'], { stdio: 'ignore' })
    return true
  } catch {
    return false
  }
}

/** Cheap reachability probe from the host (no docker involved) before paying for a full dump. */
async function sourceDbReachable(): Promise<boolean> {
  const client = new Client({
    host: SOURCE_HOST,
    port: SOURCE_PORT,
    user: SOURCE_USER,
    password: SOURCE_PASSWORD,
    database: SOURCE_DB,
    connectionTimeoutMillis: 3_000,
  })
  try {
    await client.connect()
    await client.query('select 1')
    return true
  } catch {
    return false
  } finally {
    await client.end().catch(() => {})
  }
}

const dockerIsUp = dockerAvailable()
const sourceIsReachable = dockerIsUp ? await sourceDbReachable() : false
const shouldRun = dockerIsUp && sourceIsReachable

if (!dockerIsUp) {
  console.warn(
    '[dev-db-backup-restore.test.ts] SKIPPED: docker daemon unavailable ("docker info" failed). '
    + 'Dev DB backup/restore verification did not run.',
  )
} else if (!sourceIsReachable) {
  console.warn(
    `[dev-db-backup-restore.test.ts] SKIPPED: source dev DB not reachable at `
    + `${SOURCE_HOST}:${SOURCE_PORT}/${SOURCE_DB}. Backup/restore verification did not run. `
    + 'Reproduce: start the `postgres-source` container (postgres:17.6) that publishes port 15432 '
    + 'with database agentic_worker / user dev_user, then re-run this test.',
  )
}

/**
 * Docker-gated dev DB backup/restore verification (plan Task 3). Read-only against the source —
 * takes a logical pg_dump and restores it into a disposable fresh container. Skipped with a
 * logged reason (never fabricated) when Docker or the source DB is unavailable.
 */
describe.skipIf(!shouldRun)('dev DB backup/restore (Docker-gated, read-only against source)', () => {
  let dumpSql = ''
  let restoreContainerId = ''
  let restoreHostPort = 0
  let restoreClient: Client | undefined

  beforeAll(async () => {
    // 1. Logical backup via v17 client container, read-only against the source.
    dumpSql = execFileSync('docker', [
      'run', '--rm',
      '-e', `PGPASSWORD=${SOURCE_PASSWORD}`,
      CLIENT_IMAGE,
      'pg_dump',
      '-h', 'host.docker.internal',
      '-p', String(SOURCE_PORT),
      '-U', SOURCE_USER,
      '--no-owner',
      '--no-privileges',
      SOURCE_DB,
    ], { encoding: 'utf-8', maxBuffer: 50 * 1024 * 1024 })

    // 2. Fresh, empty v17 container to restore into.
    restoreContainerId = execFileSync('docker', [
      'run', '-d', '-P',
      '-e', `POSTGRES_USER=${RESTORE_USER}`,
      '-e', `POSTGRES_PASSWORD=${RESTORE_PASSWORD}`,
      '-e', `POSTGRES_DB=${RESTORE_DB}`,
      RESTORE_IMAGE,
    ]).toString().trim()

    const portOutput = execFileSync('docker', ['port', restoreContainerId, '5432/tcp']).toString().trim()
    const match = portOutput.match(/:(\d+)\s*$/)
    if (!match) {
      throw new Error(`could not parse host port from "docker port" output: ${portOutput}`)
    }
    restoreHostPort = Number(match[1])

    restoreClient = await connectWithRetry(restoreHostPort, 30_000)

    // 3. Restore via psql (not the node pg driver) fed through stdin — psql natively understands
    // the COPY FROM stdin blocks that pg_dump's plain-SQL output emits.
    execFileSync('docker', [
      'exec', '-i', restoreContainerId,
      'psql', '-v', 'ON_ERROR_STOP=1', '-U', RESTORE_USER, '-d', RESTORE_DB,
    ], { input: dumpSql, encoding: 'utf-8', maxBuffer: 50 * 1024 * 1024 })
  }, 90_000)

  afterAll(async () => {
    await restoreClient?.end()
    if (restoreContainerId) {
      execFileSync('docker', ['rm', '-f', restoreContainerId], { stdio: 'ignore' })
    }
  }, 30_000)

  it('produces a non-trivial dump', () => {
    console.log(`[dev-db-backup-restore] dump size: ${dumpSql.length} bytes`)
    expect(dumpSql.length).toBeGreaterThan(0)
    expect(dumpSql).toMatch(/CREATE TABLE/i)
  })

  it('restores every expected public table from the Java Flyway schema', async () => {
    const result = await restoreClient!.query(
      `select table_name from information_schema.tables where table_schema = 'public'`,
    )
    const actualTables = result.rows.map((row) => row.table_name as string).sort()
    console.log(`[dev-db-backup-restore] restored tables: ${actualTables.join(', ')}`)
    expect(actualTables).toEqual(EXPECTED_PUBLIC_TABLES)
  })
})

async function connectWithRetry(port: number, timeoutMs: number): Promise<Client> {
  const deadline = Date.now() + timeoutMs
  let lastError: unknown
  while (Date.now() < deadline) {
    const candidate = new Client({
      host: '127.0.0.1',
      port,
      user: RESTORE_USER,
      password: RESTORE_PASSWORD,
      database: RESTORE_DB,
    })
    try {
      await candidate.connect()
      return candidate
    } catch (error) {
      lastError = error
      await candidate.end().catch(() => {})
      await new Promise((r) => setTimeout(r, 500))
    }
  }
  throw new Error(`postgres container did not become ready within ${timeoutMs}ms: ${String(lastError)}`)
}
