import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { GenericContainer, type StartedTestContainer } from 'testcontainers'
import { drizzle } from 'drizzle-orm/node-postgres'
import { Pool } from 'pg'
import { sql } from 'drizzle-orm'
import { controlPlane } from '@agentic-worker/db'
import { withOutbox } from '../server/utils/outbox.js'

let container: StartedTestContainer
let pool: Pool
let db: ReturnType<typeof drizzle<typeof controlPlane>>

beforeAll(async () => {
  container = await new GenericContainer('postgres:16-alpine')
    .withEnvironment({ POSTGRES_PASSWORD: 'test', POSTGRES_DB: 'test' })
    .withExposedPorts(5432)
    .start()
  pool = new Pool({
    host: container.getHost(),
    port: container.getMappedPort(5432),
    user: 'postgres',
    password: 'test',
    database: 'test',
  })
  db = drizzle(pool, { schema: controlPlane })
  await pool.query('CREATE SCHEMA IF NOT EXISTS control_plane')
  await pool.query(`CREATE TABLE control_plane.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz
  )`)
}, 60_000)

afterAll(async () => {
  await pool.end()
  await container.stop()
})

describe('withOutbox', () => {
  it('inserts one outbox row in the same transaction as the wrapped work', async () => {
    const aggregateId = '11111111-1111-1111-1111-111111111111'

    const result = await withOutbox(
      db,
      { aggregateType: 'issue', aggregateId, eventType: 'ISSUE_CREATED', payload: { title: 'test' } },
      async () => 'work-result',
    )

    expect(result).toBe('work-result')
    const rows = await db.execute(sql`select * from control_plane.outbox_events`)
    expect(rows.rows).toHaveLength(1)
    expect(rows.rows[0].event_type).toBe('ISSUE_CREATED')
  })

  it('rolls back the outbox insert if the wrapped work throws', async () => {
    await expect(withOutbox(
      db,
      { aggregateType: 'issue', aggregateId: '22222222-2222-2222-2222-222222222222', eventType: 'X', payload: {} },
      async () => { throw new Error('boom') },
    )).rejects.toThrow('boom')

    const rows = await db.execute(sql`select * from control_plane.outbox_events where event_type = 'X'`)
    expect(rows.rows).toHaveLength(0)
  })
})
