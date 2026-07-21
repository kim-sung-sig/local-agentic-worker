import { afterAll, describe, expect, it } from 'vitest'
import { setup, $fetch, url as testUrl } from '@nuxt/test-utils/e2e'
import { fileURLToPath } from 'node:url'
import { getDb, closeDb } from '../server/utils/db.js'
import { controlPlane } from '@agentic-worker/db'

interface SeededProject {
  projectId: string
  cookieHeader: string
}

interface SeededNotification {
  notificationId: string
  internalId: bigint
}

/**
 * Registers a fresh user via the real auth route and extracts both the new user's id
 * (from the response body) and the `session_token` cookie pair (from the `set-cookie`
 * response header) - every Stage 3 route now requires a session (Task 4's guards), and
 * the SSE stream route reads the same cookie an `EventSource` would send automatically
 * same-origin.
 */
async function registerAndLogin(): Promise<{ userId: string, cookieHeader: string }> {
  // Plain `fetch` against the running test server (not `$fetch`) so the `set-cookie`
  // response header is directly readable, same pattern as `readFirstChunk` below.
  const response = await fetch(testUrl('/api/auth/register'), {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: `sse-${Math.random()}@example.com`, password: 'correct horse battery staple' }),
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
  return { userId: user.id, cookieHeader: sessionCookie.trim().split(';')[0]! }
}

async function seedProject(): Promise<SeededProject> {
  const { cookieHeader } = await registerAndLogin()
  const project = await $fetch<{ id: string }>('/api/projects', {
    method: 'POST',
    headers: { cookie: cookieHeader },
    body: {
      name: `SSE Project ${Math.random()}`,
      repositoryUri: `https://github.com/acme/${Math.random()}.git`,
    },
  })
  // registerProject (Task 5) now auto-creates an OWNER membership for the registering
  // user in the same transaction as the project insert, so no manual seed is needed here
  // (it would otherwise duplicate-violate memberships_user_id_project_id_unique).
  return { projectId: project.id, cookieHeader }
}

async function seedNotification(projectId: string, title: string): Promise<SeededNotification> {
  const [row] = await getDb().insert(controlPlane.notifications).values({
    eventKey: `event-${Math.random()}`,
    projectId,
    type: 'ISSUE_CREATED',
    severity: 'INFO',
    title,
  }).returning({ id: controlPlane.notifications.id, notificationId: controlPlane.notifications.notificationId })
  return { notificationId: row!.notificationId, internalId: BigInt(row!.id) }
}

async function readFirstChunk(
  streamUrl: string,
  lastEventId: string,
  cookieHeader: string,
): Promise<{ contentType: string | null, chunk: string }> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 500)
  try {
    const response = await fetch(streamUrl, {
      // EventSource sends cookies automatically same-origin; a plain fetch needs the
      // cookie header set explicitly to exercise the same guarded path.
      headers: { 'Last-Event-ID': lastEventId, cookie: cookieHeader },
      signal: controller.signal,
    })
    const contentType = response.headers.get('content-type')
    const reader = response.body!.getReader()
    const { value } = await reader.read()
    const chunk = new TextDecoder().decode(value)
    await reader.cancel().catch(() => {})
    return { contentType, chunk }
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      return { contentType: null, chunk: '' }
    }
    throw error
  } finally {
    clearTimeout(timeout)
  }
}

describe('GET /api/projects/:projectId/notifications/stream', async () => {
  await setup({ rootDir: fileURLToPath(new URL('..', import.meta.url)) })

  afterAll(async () => {
    await closeDb()
  })

  it('replays notifications after Last-Event-ID and includes an id: line browsers can echo back', async () => {
    const { projectId, cookieHeader } = await seedProject()
    const first = await seedNotification(projectId, 'First')
    const second = await seedNotification(projectId, 'Second')

    const streamUrl = testUrl(`/api/projects/${projectId}/notifications/stream`)
    const { contentType, chunk } = await readFirstChunk(streamUrl, first.internalId.toString(), cookieHeader)

    expect(contentType).toContain('text/event-stream')
    expect(chunk).toContain('event: notification.created')
    expect(chunk).toContain(`id: ${second.internalId.toString()}`)
    expect(chunk).not.toContain(`id: ${first.internalId.toString()}`)
  })

  it('emits event: reset when Last-Event-ID is not a valid cursor', async () => {
    const { projectId, cookieHeader } = await seedProject()

    const streamUrl = testUrl(`/api/projects/${projectId}/notifications/stream`)
    const { chunk } = await readFirstChunk(streamUrl, 'not-a-number', cookieHeader)

    expect(chunk).toContain('event: reset')
  })
})
