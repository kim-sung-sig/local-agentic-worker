import { afterAll, describe, expect, it } from 'vitest'
import { setup, $fetch, url as testUrl } from '@nuxt/test-utils/e2e'
import { fileURLToPath } from 'node:url'
import { getDb, closeDb } from '../server/utils/db.js'
import { controlPlane } from '@agentic-worker/db'

await setup({ rootDir: fileURLToPath(new URL('..', import.meta.url)) })

/**
 * Inserts a real `control_plane.users` row for use as an approver id. There is no
 * user-registration API route yet (Stage 4 adds auth) - reuses the same `getDb()`
 * singleton the app itself connects through, since `@nuxt/test-utils`'s `setup()` boots
 * the actual app process against this same `DATABASE_URL`.
 */
async function insertTestUser(): Promise<string> {
  const [row] = await getDb().insert(controlPlane.users).values({
    email: `e2e-approver-${Math.random()}@example.com`,
    name: 'E2E Approver',
  }).returning({ id: controlPlane.users.id })
  if (!row) {
    throw new Error('insertTestUser: insert returned no row')
  }
  return row.id
}

interface RegisteredCaller {
  userId: string
  cookieHeader: { cookie: string }
}

/**
 * Registers a fresh user via the real auth route (plain `fetch` against the running
 * test server, not `$fetch`, so the `set-cookie` response header is directly readable)
 * and extracts the `session_token` cookie so it can be threaded into subsequent
 * `$fetch` calls as a `cookie` request header (every Stage 3 route now requires a
 * session per Task 4's guards).
 */
async function registerAndLogin(email: string): Promise<RegisteredCaller> {
  const response = await fetch(testUrl('/api/auth/register'), {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password: 'correct horse battery staple' }),
  })
  const user = await response.json() as { id: string }
  const setCookieHeader = response.headers.get('set-cookie')
  if (!setCookieHeader) {
    throw new Error('registerAndLogin: no set-cookie header on register response')
  }
  const sessionCookie = setCookieHeader.split(',').find((part) => part.trim().startsWith('session_token='))
  if (!sessionCookie) {
    throw new Error('registerAndLogin: no session_token cookie in set-cookie header')
  }
  const cookiePair = sessionCookie.trim().split(';')[0]!

  return { userId: user.id, cookieHeader: { cookie: cookiePair } }
}

describe('Control Plane API golden path', () => {
  afterAll(async () => {
    await closeDb()
  })

  it('registers a project, files an issue, drafts and approves a document, and lists notifications', async () => {
    const owner = await registerAndLogin(`e2e-owner-${Math.random()}@example.com`)

    const project = await $fetch('/api/projects', {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { name: `E2E Project ${Math.random()}`, repositoryUri: `https://github.com/acme/e2e-${Math.random()}.git` },
    })
    expect(project.name).toContain('E2E Project')
    expect(project).not.toHaveProperty('credentialRef')

    // registerProject (Task 5) now auto-creates an OWNER membership for the registering
    // user in the same transaction as the project insert, so no manual seed is needed here
    // (it would otherwise duplicate-violate memberships_user_id_project_id_unique).

    const projects = await $fetch('/api/projects', { headers: owner.cookieHeader })
    expect(projects.some((p: any) => p.id === project.id)).toBe(true)

    const issue = await $fetch(`/api/projects/${project.id}/issues`, {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { title: 'First ticket' },
    })
    expect(issue.issueNumber).toBe(1)
    expect(issue.status).toBe('OPEN')

    const issues = await $fetch(`/api/projects/${project.id}/issues`, { headers: owner.cookieHeader })
    expect(issues).toHaveLength(1)

    const updated = await $fetch(`/api/issues/${issue.id}/status`, {
      method: 'PATCH',
      headers: owner.cookieHeader,
      body: { status: 'IN_PROGRESS' },
    })
    expect(updated.status).toBe('IN_PROGRESS')

    const document = await $fetch(`/api/issues/${issue.id}/documents`, {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { projectId: project.id, issueId: issue.id, kind: 'PLAN', title: 'Plan', content: 'v1' },
    })
    expect(document.latestRevision.revisionNumber).toBe(1)
    expect(document.latestRevision.approvedAt).toBeNull()

    const revision2 = await $fetch(`/api/documents/${document.id}/revisions`, {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { content: 'v2' },
    })
    expect(revision2.revisionNumber).toBe(2)

    const approverId = await insertTestUser()
    const approved = await $fetch(`/api/document-revisions/${revision2.id}/approve`, {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { approvedByUserId: approverId },
    })
    expect(approved.approvedAt).not.toBeNull()

    const notifications = await $fetch(`/api/projects/${project.id}/notifications`, { headers: owner.cookieHeader })
    expect(Array.isArray(notifications)).toBe(true)

    const unread = await $fetch(`/api/projects/${project.id}/notifications/unread-count`, { headers: owner.cookieHeader })
    expect(typeof unread.count).toBe('number')
  })
})
