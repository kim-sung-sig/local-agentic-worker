import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { GenericContainer, type StartedTestContainer } from 'testcontainers'
import { drizzle } from 'drizzle-orm/node-postgres'
import { Pool } from 'pg'
import { controlPlane } from '@agentic-worker/db'
import { registerProject, listProjects, getProject } from '../server/utils/project-service.js'
import { closeDb } from '../server/utils/db.js'

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

  process.env.DATABASE_URL = `postgresql://postgres:test@${container.getHost()}:${container.getMappedPort(5432)}/test`
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
    })

    expect(project.name).toBe('Catalog Service')
    expect(project.baseBranch).toBe('main')
    expect(project).not.toHaveProperty('credentialRef')
  })
})

describe('listProjects / getProject', () => {
  it('lists registered projects and fetches one by id', async () => {
    const created = await registerProject({ name: 'Second', repositoryUri: 'https://github.com/acme/second.git' })

    const all = await listProjects()
    expect(all.some((p) => p.id === created.id)).toBe(true)

    const fetched = await getProject(created.id)
    expect(fetched?.name).toBe('Second')
  })

  it('returns null for an unknown project id', async () => {
    expect(await getProject('00000000-0000-0000-0000-000000000000')).toBeNull()
  })
})
