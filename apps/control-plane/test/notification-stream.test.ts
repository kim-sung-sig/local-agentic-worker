import { afterAll, describe, expect, it } from 'vitest'
import { setup, $fetch, url as testUrl } from '@nuxt/test-utils/e2e'
import { fileURLToPath } from 'node:url'
import { getDb, closeDb } from '../server/utils/db.js'
import { controlPlane } from '@agentic-worker/db'

interface SeededProject {
  projectId: string
}

interface SeededNotification {
  notificationId: string
  internalId: bigint
}

async function seedProject(): Promise<SeededProject> {
  const project = await $fetch<{ id: string }>('/api/projects', {
    method: 'POST',
    body: {
      name: `SSE Project ${Math.random()}`,
      repositoryUri: `https://github.com/acme/${Math.random()}.git`,
    },
  })
  return { projectId: project.id }
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

async function readFirstChunk(streamUrl: string, lastEventId: string): Promise<{ contentType: string | null, chunk: string }> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 500)
  try {
    const response = await fetch(streamUrl, {
      headers: { 'Last-Event-ID': lastEventId },
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
    const { projectId } = await seedProject()
    const first = await seedNotification(projectId, 'First')
    const second = await seedNotification(projectId, 'Second')

    const streamUrl = testUrl(`/api/projects/${projectId}/notifications/stream`)
    const { contentType, chunk } = await readFirstChunk(streamUrl, first.internalId.toString())

    expect(contentType).toContain('text/event-stream')
    expect(chunk).toContain('event: notification.created')
    expect(chunk).toContain(`id: ${second.internalId.toString()}`)
    expect(chunk).not.toContain(`id: ${first.internalId.toString()}`)
  })

  it('emits event: reset when Last-Event-ID is not a valid cursor', async () => {
    const { projectId } = await seedProject()

    const streamUrl = testUrl(`/api/projects/${projectId}/notifications/stream`)
    const { chunk } = await readFirstChunk(streamUrl, 'not-a-number')

    expect(chunk).toContain('event: reset')
  })
})
