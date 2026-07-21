import { randomBytes, createHash } from 'node:crypto'
import { eq } from 'drizzle-orm'
import { hash, verify } from '@node-rs/argon2'
import { controlPlane } from '@agentic-worker/db'
import { getDb } from './db.js'

export interface RegisterUserInput {
  email: string
  password: string
  name?: string
}

export interface AuthenticatedUser {
  id: string
  email: string
  name: string | null
}

export interface IssuedSession {
  rawToken: string
  expiresAt: Date
}

const SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000

function toAuthenticatedUser(row: { id: string, email: string, name: string | null }): AuthenticatedUser {
  return { id: row.id, email: row.email, name: row.name }
}

function hashToken(rawToken: string): string {
  return createHash('sha256').update(rawToken).digest('hex')
}

export async function registerUser(input: RegisterUserInput): Promise<AuthenticatedUser> {
  const passwordHash = await hash(input.password)
  const [row] = await getDb().insert(controlPlane.users).values({
    email: input.email,
    name: input.name ?? null,
    passwordHash,
  }).returning()
  if (!row) {
    throw new Error('registerUser: insert returned no row')
  }
  return toAuthenticatedUser(row)
}

export async function verifyPassword(email: string, password: string): Promise<AuthenticatedUser | null> {
  const [row] = await getDb().select().from(controlPlane.users).where(eq(controlPlane.users.email, email))
  if (!row || !row.passwordHash) {
    return null
  }
  const valid = await verify(row.passwordHash, password)
  if (!valid) {
    return null
  }
  return toAuthenticatedUser(row)
}

export async function issueSession(userId: string): Promise<IssuedSession> {
  const rawToken = randomBytes(32).toString('hex')
  const expiresAt = new Date(Date.now() + SESSION_TTL_MS)
  await getDb().insert(controlPlane.sessions).values({
    userId,
    token: hashToken(rawToken),
    expiresAt,
  })
  return { rawToken, expiresAt }
}

export async function resolveSession(rawToken: string): Promise<AuthenticatedUser | null> {
  const [session] = await getDb().select().from(controlPlane.sessions)
    .where(eq(controlPlane.sessions.token, hashToken(rawToken)))
  if (!session || session.expiresAt.getTime() <= Date.now()) {
    return null
  }
  const [user] = await getDb().select().from(controlPlane.users).where(eq(controlPlane.users.id, session.userId))
  return user ? toAuthenticatedUser(user) : null
}
