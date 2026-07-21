import { afterAll, describe, expect, it } from 'vitest'
import { setup, $fetch, url as testUrl } from '@nuxt/test-utils/e2e'
import { fileURLToPath } from 'node:url'
import { getDb, closeDb } from '../server/utils/db.js'
import { controlPlane } from '@agentic-worker/db'

await setup({ rootDir: fileURLToPath(new URL('..', import.meta.url)) })

interface RegisteredCaller {
  userId: string
  cookieHeader: { cookie: string }
}

/**
 * Registers a fresh user via the real auth route (plain `fetch` against the running
 * test server, not `$fetch`, so the `set-cookie` response header is directly readable)
 * and extracts the `session_token` cookie so it can be threaded into subsequent
 * `$fetch` calls as a `cookie` request header. Same approach as `api.e2e.test.ts`.
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

describe('Control Plane authentication and authorization', () => {
  afterAll(async () => {
    await closeDb()
  })

  it('rejects an unauthenticated request to a protected route with 401', async () => {
    await expect($fetch('/api/projects')).rejects.toMatchObject({ response: { status: 401 } })
  })

  it('rejects a request to a project the caller is not a member of with 403', async () => {
    const owner = await registerAndLogin(`e2e-auth-owner-${Math.random()}@example.com`)
    const outsider = await registerAndLogin(`e2e-auth-outsider-${Math.random()}@example.com`)

    const project = await $fetch('/api/projects', {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { name: `Private project ${Math.random()}`, repositoryUri: `https://github.com/acme/private-${Math.random()}.git` },
    })

    await expect($fetch(`/api/projects/${project.id}`, { headers: outsider.cookieHeader }))
      .rejects.toMatchObject({ response: { status: 403 } })
  })

  it('requires MAINTAINER to approve a document revision - MEMBER is rejected, MAINTAINER succeeds', async () => {
    // Arrange: owner registers a project (becomes OWNER via registerProject's
    // auto-membership, Task 5). Seed a second user as MEMBER and a third as MAINTAINER
    // of that project by direct `memberships` insert - no membership-invitation API
    // exists yet (out of scope), same direct-DB-insert precedent as api.e2e.test.ts's
    // `insertTestUser`. File an issue, draft a document as the owner, add a second
    // revision to approve.
    const owner = await registerAndLogin(`e2e-auth-role-owner-${Math.random()}@example.com`)
    const member = await registerAndLogin(`e2e-auth-role-member-${Math.random()}@example.com`)
    const maintainer = await registerAndLogin(`e2e-auth-role-maintainer-${Math.random()}@example.com`)

    const project = await $fetch('/api/projects', {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { name: `Role project ${Math.random()}`, repositoryUri: `https://github.com/acme/role-${Math.random()}.git` },
    })

    await getDb().insert(controlPlane.memberships).values([
      { userId: member.userId, projectId: project.id, role: 'MEMBER' },
      { userId: maintainer.userId, projectId: project.id, role: 'MAINTAINER' },
    ])

    const issue = await $fetch(`/api/projects/${project.id}/issues`, {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { title: 'Role-gated ticket' },
    })

    const document = await $fetch(`/api/issues/${issue.id}/documents`, {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { projectId: project.id, issueId: issue.id, kind: 'PLAN', title: 'Plan', content: 'v1' },
    })

    const revision2 = await $fetch(`/api/documents/${document.id}/revisions`, {
      method: 'POST',
      headers: owner.cookieHeader,
      body: { content: 'v2' },
    })

    await expect($fetch(`/api/document-revisions/${revision2.id}/approve`, {
      method: 'POST',
      headers: member.cookieHeader,
      body: { approvedByUserId: member.userId },
    })).rejects.toMatchObject({ response: { status: 403 } })

    const approved = await $fetch(`/api/document-revisions/${revision2.id}/approve`, {
      method: 'POST',
      headers: maintainer.cookieHeader,
      body: { approvedByUserId: maintainer.userId },
    })
    expect(approved.approvedAt).not.toBeNull()
  })
})
