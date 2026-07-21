import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { sql } from 'drizzle-orm'
import { registerUser, verifyPassword, issueSession, resolveSession } from '../server/utils/auth-service.js'
import { closeDb } from '../server/utils/db.js'
import { startTestDatabase, stopTestDatabase, type TestDatabase } from './support/postgres.js'

let testDb: TestDatabase

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
}, 60_000)

afterAll(async () => {
  await closeDb()
  await stopTestDatabase(testDb)
})

describe('registerUser / verifyPassword', () => {
  it('registers a user with a hashed password, never the plaintext', async () => {
    const user = await registerUser({ email: 'dev@example.com', password: 'correct horse battery staple' })

    expect(user.email).toBe('dev@example.com')
    expect(user).not.toHaveProperty('passwordHash')

    const row = await testDb.db.execute(sql`select password_hash from control_plane.users where id = ${user.id}`)
    expect(row.rows[0]?.password_hash).not.toBe('correct horse battery staple')
  })

  it('authenticates with the correct password and rejects the wrong one', async () => {
    await registerUser({ email: 'auth-check@example.com', password: 'right-password' })

    expect(await verifyPassword('auth-check@example.com', 'right-password')).not.toBeNull()
    expect(await verifyPassword('auth-check@example.com', 'wrong-password')).toBeNull()
  })

  it('returns null for an unknown email rather than throwing', async () => {
    expect(await verifyPassword('nobody@example.com', 'anything')).toBeNull()
  })
})

describe('issueSession / resolveSession', () => {
  it('issues a session whose raw token resolves back to the user, but the stored hash does not', async () => {
    const user = await registerUser({ email: 'session-check@example.com', password: 'p' })

    const session = await issueSession(user.id)
    const resolved = await resolveSession(session.rawToken)
    expect(resolved?.id).toBe(user.id)

    const rows = await testDb.db.execute(sql`select token from control_plane.sessions where user_id = ${user.id}`)
    expect(rows.rows[0]?.token).not.toBe(session.rawToken)
  })

  it('returns null for an unknown or malformed token', async () => {
    expect(await resolveSession('not-a-real-token')).toBeNull()
  })
})
