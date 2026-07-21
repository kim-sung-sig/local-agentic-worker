import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { GenericContainer, type StartedTestContainer } from 'testcontainers'
import { drizzle } from 'drizzle-orm/node-postgres'
import { Pool } from 'pg'
import { sql } from 'drizzle-orm'
import { controlPlane } from '@agentic-worker/db'
import { createIssue, listIssuesByProject, getIssue, updateIssueStatus } from '../server/utils/issue-service.js'
import { closeDb } from '../server/utils/db.js'

let container: StartedTestContainer
let pool: Pool
let db: ReturnType<typeof drizzle<typeof controlPlane>>

async function insertTestProject(): Promise<string> {
  const result = await pool.query(
    `INSERT INTO control_plane.projects (name, repository_uri) VALUES ($1, $2) RETURNING id`,
    [`Project ${Math.random()}`, `https://github.com/acme/${Math.random()}.git`],
  )
  return result.rows[0].id
}

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
  await pool.query(`CREATE TABLE control_plane.projects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    local_path text,
    base_branch text NOT NULL DEFAULT 'main',
    repository_uri text,
    credential_ref text,
    created_at timestamptz NOT NULL DEFAULT now()
  )`)
  await pool.query(`CREATE UNIQUE INDEX projects_repository_uri_unique
    ON control_plane.projects (repository_uri) WHERE repository_uri IS NOT NULL`)
  await pool.query(`CREATE TABLE control_plane.issues (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES control_plane.projects(id),
    issue_number integer NOT NULL,
    title text NOT NULL,
    description text,
    priority text,
    status text NOT NULL DEFAULT 'OPEN',
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(project_id, issue_number)
  )`)
  await pool.query(`CREATE INDEX issues_project_id_idx ON control_plane.issues (project_id)`)
  await pool.query(`CREATE TABLE control_plane.outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz
  )`)

  process.env.DATABASE_URL = `postgresql://postgres:test@${container.getHost()}:${container.getMappedPort(5432)}/test`
}, 60_000)

afterAll(async () => {
  await closeDb()
  await pool.end()
  await container.stop()
})

describe('createIssue', () => {
  it('assigns issueNumber 1 for the first issue in a project, 2 for the second', async () => {
    const projectId = await insertTestProject()

    const first = await createIssue(projectId, { title: 'First issue' })
    const second = await createIssue(projectId, { title: 'Second issue' })

    expect(first.issueNumber).toBe(1)
    expect(second.issueNumber).toBe(2)
    expect(first.status).toBe('OPEN')
  })

  it('writes one ISSUE_CREATED outbox row per created issue', async () => {
    const projectId = await insertTestProject()
    const issue = await createIssue(projectId, { title: 'Outbox check' })

    const rows = await db.execute(sql`select * from control_plane.outbox_events where aggregate_id = ${issue.id}`)
    expect(rows.rows).toHaveLength(1)
    expect(rows.rows[0].event_type).toBe('ISSUE_CREATED')
  })
})

describe('listIssuesByProject / getIssue / updateIssueStatus', () => {
  it('lists issues for a project and updates status', async () => {
    const projectId = await insertTestProject()
    const issue = await createIssue(projectId, { title: 'Status flow' })

    const list = await listIssuesByProject(projectId)
    expect(list.map((i) => i.id)).toContain(issue.id)

    const updated = await updateIssueStatus(issue.id, 'IN_PROGRESS')
    expect(updated?.status).toBe('IN_PROGRESS')

    const fetched = await getIssue(issue.id)
    expect(fetched?.status).toBe('IN_PROGRESS')
  })

  it('returns null updating an unknown issue', async () => {
    expect(await updateIssueStatus('00000000-0000-0000-0000-000000000000', 'DONE')).toBeNull()
  })
})
