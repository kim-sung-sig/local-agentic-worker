import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { Client } from 'pg'

const __dirname = dirname(fileURLToPath(import.meta.url))
const CONTROL_PLANE_SQL = resolve(__dirname, '../drizzle/control-plane/0000_minor_nekra.sql')
const ENGINE_SQL = resolve(__dirname, '../drizzle/engine/0000_supreme_devos.sql')
const IMAGE = 'postgres:16-alpine'
const PG_USER = 'migration_test'
const PG_PASSWORD = 'migration_test'
const PG_DB = 'migration_test'

const EXPECTED_TABLES: Record<string, string[]> = {
  control_plane: [
    'projects',
    'issues',
    'documents',
    'document_revisions',
    'document_artifacts',
    'notifications',
    'users',
    'memberships',
    'sessions',
    'outbox_events',
  ],
  engine: ['workflow_runs', 'stage_gates', 'attempt_records'],
}

function dockerAvailable(): boolean {
  try {
    execFileSync('docker', ['info'], { stdio: 'ignore' })
    return true
  } catch {
    return false
  }
}

const dockerIsUp = dockerAvailable()

/**
 * Docker-gated migration apply verification (plan Task 2). If Docker is unavailable
 * this suite is skipped with a logged reason instead of fabricating a pass.
 */
describe.skipIf(!dockerIsUp)('migration apply against empty PostgreSQL (Docker-gated)', () => {
  let containerId = ''
  let hostPort = 0
  let client: Client | undefined

  beforeAll(async () => {
    containerId = execFileSync('docker', [
      'run', '-d', '-P',
      '-e', `POSTGRES_USER=${PG_USER}`,
      '-e', `POSTGRES_PASSWORD=${PG_PASSWORD}`,
      '-e', `POSTGRES_DB=${PG_DB}`,
      IMAGE,
    ]).toString().trim()

    const portOutput = execFileSync('docker', ['port', containerId, '5432/tcp']).toString().trim()
    const match = portOutput.match(/:(\d+)\s*$/)
    if (!match) {
      throw new Error(`could not parse host port from "docker port" output: ${portOutput}`)
    }
    hostPort = Number(match[1])

    client = await connectWithRetry(hostPort, 30_000)

    const controlPlaneSql = readFileSync(CONTROL_PLANE_SQL, 'utf-8')
    const engineSql = readFileSync(ENGINE_SQL, 'utf-8')
    await client.query(controlPlaneSql)
    await client.query(engineSql)
  }, 60_000)

  afterAll(async () => {
    await client?.end()
    if (containerId) {
      execFileSync('docker', ['rm', '-f', containerId], { stdio: 'ignore' })
    }
  }, 30_000)

  it('creates both control_plane and engine schemas', async () => {
    const result = await client!.query(
      `select schema_name from information_schema.schemata where schema_name in ('control_plane', 'engine')`,
    )
    const schemas = result.rows.map((row) => row.schema_name).sort()
    expect(schemas).toEqual(['control_plane', 'engine'])
  })

  it.each(Object.entries(EXPECTED_TABLES))('creates every mapped table in schema %s', async (schema, expectedTables) => {
    const result = await client!.query(
      `select table_name from information_schema.tables where table_schema = $1`,
      [schema],
    )
    const actualTables = result.rows.map((row) => row.table_name).sort()
    expect(actualTables).toEqual([...expectedTables].sort())
  })
})

describe('generated migration SQL boundary check (runs without Docker)', () => {
  it('control-plane migration has no reference to the engine schema', () => {
    const sql = readFileSync(CONTROL_PLANE_SQL, 'utf-8')
    expect(sql).not.toMatch(/REFERENCES\s+"engine"/i)
  })

  it('engine migration has no reference to the control_plane schema', () => {
    const sql = readFileSync(ENGINE_SQL, 'utf-8')
    expect(sql).not.toMatch(/REFERENCES\s+"control_plane"/i)
  })
})

if (!dockerIsUp) {
  console.warn(
    '[migration-apply.test.ts] SKIPPED: docker daemon unavailable ("docker info" failed). '
    + 'Docker-gated empty-Postgres migration apply verification did not run.',
  )
}

async function connectWithRetry(port: number, timeoutMs: number): Promise<Client> {
  const deadline = Date.now() + timeoutMs
  let lastError: unknown
  while (Date.now() < deadline) {
    const candidate = new Client({
      host: '127.0.0.1',
      port,
      user: PG_USER,
      password: PG_PASSWORD,
      database: PG_DB,
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
