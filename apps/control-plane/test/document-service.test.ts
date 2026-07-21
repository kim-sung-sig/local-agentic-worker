import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { sql } from 'drizzle-orm'
import { createDocument, addRevision, approveRevision } from '../server/utils/document-service.js'
import { closeDb } from '../server/utils/db.js'
import { startTestDatabase, stopTestDatabase, type TestDatabase } from './support/postgres.js'

let testDb: TestDatabase

async function insertTestProject(): Promise<string> {
  const result = await testDb.pool.query(
    `INSERT INTO control_plane.projects (name, repository_uri) VALUES ($1, $2) RETURNING id`,
    [`Project ${Math.random()}`, `https://github.com/acme/${Math.random()}.git`],
  )
  return result.rows[0].id
}

async function insertTestUser(): Promise<string> {
  const result = await testDb.pool.query(
    `INSERT INTO control_plane.users (email) VALUES ($1) RETURNING id`,
    [`user-${Math.random()}@example.com`],
  )
  return result.rows[0].id
}

beforeAll(async () => {
  testDb = await startTestDatabase()
  await testDb.pool.query('CREATE SCHEMA IF NOT EXISTS control_plane')
  await testDb.pool.query(`CREATE TABLE control_plane.projects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    local_path text,
    base_branch text NOT NULL DEFAULT 'main',
    repository_uri text,
    credential_ref text,
    created_at timestamptz NOT NULL DEFAULT now()
  )`)
  await testDb.pool.query(`CREATE UNIQUE INDEX projects_repository_uri_unique
    ON control_plane.projects (repository_uri) WHERE repository_uri IS NOT NULL`)
  await testDb.pool.query(`CREATE TABLE control_plane.issues (
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
  await testDb.pool.query(`CREATE TYPE control_plane.document_kind AS ENUM (
    'PROMPT_TEMPLATE', 'DEVELOPMENT_GUIDE', 'QA_GUIDE', 'PLAN', 'IMPLEMENTATION_PLAN',
    'DEVELOPMENT_RESULT', 'QA_REPORT'
  )`)
  await testDb.pool.query(`CREATE TABLE control_plane.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL,
    name text,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(email)
  )`)
  await testDb.pool.query(`CREATE TABLE control_plane.documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES control_plane.projects(id),
    issue_id uuid REFERENCES control_plane.issues(id),
    kind control_plane.document_kind NOT NULL,
    title text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
  )`)
  await testDb.pool.query(`CREATE INDEX documents_project_id_idx ON control_plane.documents (project_id)`)
  await testDb.pool.query(`CREATE TABLE control_plane.document_revisions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id uuid NOT NULL REFERENCES control_plane.documents(id),
    revision_number integer NOT NULL,
    content text NOT NULL,
    approved_at timestamptz,
    approved_by_user_id uuid REFERENCES control_plane.users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(document_id, revision_number)
  )`)
  await testDb.pool.query(`CREATE TABLE control_plane.outbox_events (
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
  await closeDb()
  await stopTestDatabase(testDb)
})

describe('createDocument', () => {
  it('creates a document with its first revision at revisionNumber 1', async () => {
    const projectId = await insertTestProject()

    const doc = await createDocument({ projectId, kind: 'PLAN', title: 'Initial plan', content: 'Do the thing' })

    expect(doc.kind).toBe('PLAN')
    expect(doc.latestRevision.revisionNumber).toBe(1)
    expect(doc.latestRevision.content).toBe('Do the thing')
    expect(doc.latestRevision.approvedAt).toBeNull()
  })

  it('writes no outbox event on document creation', async () => {
    const projectId = await insertTestProject()
    const doc = await createDocument({ projectId, kind: 'PLAN', title: 'No outbox', content: 'v1' })

    const rows = await testDb.db.execute(
      sql`select * from control_plane.outbox_events where aggregate_id = ${doc.id} or aggregate_id = ${doc.latestRevision.id}`,
    )
    expect(rows.rows).toHaveLength(0)
  })
})

describe('addRevision', () => {
  it('appends revisionNumber 2 without altering revision 1', async () => {
    const projectId = await insertTestProject()
    const doc = await createDocument({ projectId, kind: 'PLAN', title: 'Plan', content: 'v1' })

    const revision2 = await addRevision(doc.id, 'v2')

    expect(revision2.revisionNumber).toBe(2)
    expect(revision2.content).toBe('v2')
  })
})

describe('approveRevision', () => {
  it('sets approvedAt/approvedByUserId and writes one outbox event', async () => {
    const projectId = await insertTestProject()
    const userId = await insertTestUser()
    const doc = await createDocument({ projectId, kind: 'QA_REPORT', title: 'QA', content: 'passed' })

    const approved = await approveRevision(doc.latestRevision.id, userId)

    expect(approved?.approvedAt).not.toBeNull()
    expect(approved?.approvedByUserId).toBe(userId)

    const rows = await testDb.db.execute(
      sql`select * from control_plane.outbox_events where aggregate_id = ${doc.latestRevision.id}`,
    )
    expect(rows.rows).toHaveLength(1)
    expect(rows.rows[0].event_type).toBe('DOCUMENT_REVISION_APPROVED')
  })

  it('returns null approving an unknown revision id', async () => {
    const userId = await insertTestUser()
    expect(await approveRevision('00000000-0000-0000-0000-000000000000', userId)).toBeNull()
  })
})
