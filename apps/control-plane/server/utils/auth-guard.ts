import { and, eq } from 'drizzle-orm'
import { createError, getCookie, type H3Event } from 'h3'
import { controlPlane } from '@agentic-worker/db'
import { resolveSession, type AuthenticatedUser } from './auth-service.js'
import { getDb } from './db.js'

export type ProjectRole = 'OWNER' | 'MAINTAINER' | 'MEMBER'

export const ROLE_RANK: Record<ProjectRole, number> = {
  OWNER: 2,
  MAINTAINER: 1,
  MEMBER: 0,
}

/** Resolves the caller's session from the `session_token` cookie, or throws 401. */
export async function requireSession(event: H3Event): Promise<AuthenticatedUser> {
  const rawToken = getCookie(event, 'session_token')
  const user = rawToken ? await resolveSession(rawToken) : null
  if (!user) {
    throw createError({ statusCode: 401, statusMessage: 'Not authenticated' })
  }
  return user
}

/**
 * Requires an authenticated session AND a project membership whose role rank meets
 * `minRole`. Checks authentication first so an unauthenticated caller gets 401, not 403.
 */
export async function requireProjectRole(
  event: H3Event,
  projectId: string,
  minRole: ProjectRole,
): Promise<AuthenticatedUser> {
  const user = await requireSession(event)

  const [membership] = await getDb().select().from(controlPlane.memberships)
    .where(and(eq(controlPlane.memberships.userId, user.id), eq(controlPlane.memberships.projectId, projectId)))

  const rank = membership ? ROLE_RANK[membership.role as ProjectRole] : undefined

  if (rank === undefined || rank < ROLE_RANK[minRole]) {
    throw createError({ statusCode: 403, statusMessage: 'Not a member of this project' })
  }

  return user
}
