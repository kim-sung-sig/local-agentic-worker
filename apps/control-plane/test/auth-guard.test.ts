// Test approach: h3's installed version (see node_modules/h3/dist/index.mjs) exposes a real
// `createEvent(req, res)` factory whose H3Event constructor does nothing but
// `this.node = { req, res }` — no Nitro runtime is involved. getCookie(event, name) reads
// `event.node.req.headers.cookie` (parsed with `cookie`'s `parse`), so a minimal fake
// IncomingMessage-shaped object (`{ headers: { cookie }, method, url }`) plus an empty fake
// ServerResponse (`{}`, unused by getCookie/requireSession/requireProjectRole) is a faithful,
// non-fragile way to build a real H3Event for these guards. This avoids both a hand-rolled
// event mock that diverges from h3's real shape, and folding these assertions into Task 5's
// e2e suite (which isn't needed since h3 gives us a clean, real factory).
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { createEvent, type H3Event } from 'h3'
import { issueSession, registerUser } from '../server/utils/auth-service.js'
import { closeDb, getDb } from '../server/utils/db.js'
import { controlPlane } from '@agentic-worker/db'
import { ROLE_RANK, requireProjectRole, requireSession } from '../server/utils/auth-guard.js'
import { startTestDatabase, stopTestDatabase, type TestDatabase } from './support/postgres.js'

let testDb: TestDatabase

function fakeEvent(cookieHeader?: string): H3Event {
  const req = {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    method: 'GET',
    url: '/',
  } as unknown as import('node:http').IncomingMessage
  const res = {} as unknown as import('node:http').ServerResponse
  return createEvent(req, res)
}

beforeAll(async () => {
  testDb = await startTestDatabase()
  await testDb.pool.query('CREATE SCHEMA IF NOT EXISTS control_plane')
  await testDb.pool.query(`CREATE TABLE control_plane.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL,
    name text,
    password_hash text,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(email)
  )`)
  await testDb.pool.query(`CREATE TABLE control_plane.sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES control_plane.users(id),
    token text NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(token)
  )`)
  await testDb.pool.query(`CREATE TABLE control_plane.projects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    local_path text,
    base_branch text NOT NULL DEFAULT 'main',
    repository_uri text,
    credential_ref text,
    created_at timestamptz NOT NULL DEFAULT now()
  )`)
  await testDb.pool.query(`CREATE TABLE control_plane.memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES control_plane.users(id),
    project_id uuid NOT NULL REFERENCES control_plane.projects(id),
    role text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id, project_id)
  )`)
}, 60_000)

afterAll(async () => {
  await closeDb()
  await stopTestDatabase(testDb)
})

describe('ROLE_RANK', () => {
  it('orders OWNER > MAINTAINER > MEMBER', () => {
    expect(ROLE_RANK.OWNER).toBeGreaterThan(ROLE_RANK.MAINTAINER)
    expect(ROLE_RANK.MAINTAINER).toBeGreaterThan(ROLE_RANK.MEMBER)
  })
})

describe('requireSession', () => {
  it('throws 401 when there is no session_token cookie', async () => {
    await expect(requireSession(fakeEvent())).rejects.toMatchObject({ statusCode: 401 })
  })

  it('throws 401 when the session_token cookie does not resolve to a session', async () => {
    await expect(requireSession(fakeEvent('session_token=garbage'))).rejects.toMatchObject({ statusCode: 401 })
  })

  it('returns the AuthenticatedUser for a valid session_token cookie', async () => {
    const user = await registerUser({ email: 'guard-session@example.com', password: 'p' })
    const session = await issueSession(user.id)

    const resolved = await requireSession(fakeEvent(`session_token=${session.rawToken}`))
    expect(resolved.id).toBe(user.id)
  })
})

describe('requireProjectRole', () => {
  async function seedProject(): Promise<string> {
    const [project] = await getDb().insert(controlPlane.projects).values({ name: 'guard-test-project' }).returning()
    if (!project) throw new Error('failed to seed project')
    return project.id
  }

  it('throws 401 when unauthenticated, before any membership lookup', async () => {
    const projectId = await seedProject()
    await expect(requireProjectRole(fakeEvent(), projectId, 'MEMBER')).rejects.toMatchObject({ statusCode: 401 })
  })

  it('throws 403 when the user has no membership row for the project', async () => {
    const user = await registerUser({ email: 'guard-no-membership@example.com', password: 'p' })
    const session = await issueSession(user.id)
    const projectId = await seedProject()

    await expect(
      requireProjectRole(fakeEvent(`session_token=${session.rawToken}`), projectId, 'MEMBER'),
    ).rejects.toMatchObject({ statusCode: 403 })
  })

  it('throws 403 when the membership role rank is below minRole', async () => {
    const user = await registerUser({ email: 'guard-low-role@example.com', password: 'p' })
    const session = await issueSession(user.id)
    const projectId = await seedProject()
    await getDb().insert(controlPlane.memberships).values({ userId: user.id, projectId, role: 'MEMBER' })

    await expect(
      requireProjectRole(fakeEvent(`session_token=${session.rawToken}`), projectId, 'MAINTAINER'),
    ).rejects.toMatchObject({ statusCode: 403 })
  })

  it('throws 403 when the membership role is not a known ROLE_RANK key (fail closed)', async () => {
    const user = await registerUser({ email: 'guard-unknown-role@example.com', password: 'p' })
    const session = await issueSession(user.id)
    const projectId = await seedProject()
    await getDb().insert(controlPlane.memberships).values({ userId: user.id, projectId, role: 'BYSTANDER' })

    await expect(
      requireProjectRole(fakeEvent(`session_token=${session.rawToken}`), projectId, 'MEMBER'),
    ).rejects.toMatchObject({ statusCode: 403 })
  })

  it('returns the AuthenticatedUser when the membership role rank satisfies minRole', async () => {
    const user = await registerUser({ email: 'guard-happy-path@example.com', password: 'p' })
    const session = await issueSession(user.id)
    const projectId = await seedProject()
    await getDb().insert(controlPlane.memberships).values({ userId: user.id, projectId, role: 'OWNER' })

    const resolved = await requireProjectRole(fakeEvent(`session_token=${session.rawToken}`), projectId, 'MAINTAINER')
    expect(resolved.id).toBe(user.id)
  })
})
