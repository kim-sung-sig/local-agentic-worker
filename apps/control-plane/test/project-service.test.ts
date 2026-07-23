import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { GenericContainer, type StartedTestContainer } from 'testcontainers'
import { drizzle } from 'drizzle-orm/node-postgres'
import { and, eq } from 'drizzle-orm'
import { Pool } from 'pg'
import { controlPlane } from '@agentic-worker/db'
import { registerProject, listProjects, getProject } from '../server/utils/project-service.js'
import { closeDb } from '../server/utils/db.js'

let container: StartedTestContainer
let pool: Pool
let db: ReturnType<typeof drizzle<typeof controlPlane>>
let ownerUserId: string

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
  await pool.query(`CREATE TABLE control_plane.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL UNIQUE,
    name text,
    password_hash text,
    created_at timestamptz NOT NULL DEFAULT now()
  )`)
  await pool.query(`CREATE TABLE control_plane.memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES control_plane.users(id),
    project_id uuid NOT NULL REFERENCES control_plane.projects(id),
    role text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, project_id)
  )`)

  process.env.DATABASE_URL = `postgresql://postgres:test@${container.getHost()}:${container.getMappedPort(5432)}/test`

  const [seededUser] = await pool.query(
    `INSERT INTO control_plane.users (email, name) VALUES ($1, $2) RETURNING id`,
    ['owner@example.com', 'Owner'],
  ).then((result) => result.rows as { id: string }[])
  if (!seededUser) {
    throw new Error('beforeAll: failed to seed owner user')
  }
  ownerUserId = seededUser.id
}, 60_000)

afterAll(async () => {
  await closeDb()
  await pool.end()
  await container.stop()
})

describe('registerProject', () => {
  it('persists a project and never returns credentialRef', async () => {
    const project = await registerProject({
      name: 'Catalog Service',
      repositoryUri: 'https://github.com/acme/catalog.git',
      credentialRef: 'secret-ref-should-not-leak',
    }, ownerUserId)

    expect(project.name).toBe('Catalog Service')
    expect(project.baseBranch).toBe('main')
    expect(project).not.toHaveProperty('credentialRef')
  })

  it('creates an OWNER membership for the registering user', async () => {
    const project = await registerProject({
      name: 'Owned Service',
      repositoryUri: 'https://github.com/acme/owned.git',
    }, ownerUserId)

    const [membership] = await db.select().from(controlPlane.memberships)
      .where(and(eq(controlPlane.memberships.userId, ownerUserId), eq(controlPlane.memberships.projectId, project.id)))

    expect(membership?.role).toBe('OWNER')
  })
})

describe('listProjects / getProject', () => {
  it('lists only projects with a membership for the requested user', async () => {
    const created = await registerProject({ name: 'Second', repositoryUri: 'https://github.com/acme/second.git' }, ownerUserId)
    const [otherUser] = await pool.query(
      `INSERT INTO control_plane.users (email, name) VALUES ($1, $2) RETURNING id`,
      ['other@example.com', 'Other'],
    ).then((result) => result.rows as { id: string }[])
    if (!otherUser) {
      throw new Error('listProjects: failed to seed other user')
    }
    const hidden = await registerProject({ name: 'Hidden', repositoryUri: 'https://github.com/acme/hidden.git' }, otherUser.id)

    const projects = await listProjects(ownerUserId)
    expect(projects.some((project) => project.id === created.id)).toBe(true)
    expect(projects.some((project) => project.id === hidden.id)).toBe(false)

    const fetched = await getProject(created.id)
    expect(fetched?.name).toBe('Second')
  })

  it('returns null for an unknown project id', async () => {
    expect(await getProject('00000000-0000-0000-0000-000000000000')).toBeNull()
  })
})
