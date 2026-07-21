import { afterAll, describe, expect, it } from 'vitest'
import { setup, $fetch } from '@nuxt/test-utils/e2e'
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

describe('Control Plane API golden path', () => {
  afterAll(async () => {
    await closeDb()
  })

  it('registers a project, files an issue, drafts and approves a document, and lists notifications', async () => {
    const project = await $fetch('/api/projects', {
      method: 'POST',
      body: { name: `E2E Project ${Math.random()}`, repositoryUri: `https://github.com/acme/e2e-${Math.random()}.git` },
    })
    expect(project.name).toContain('E2E Project')
    expect(project).not.toHaveProperty('credentialRef')

    const projects = await $fetch('/api/projects')
    expect(projects.some((p: any) => p.id === project.id)).toBe(true)

    const issue = await $fetch(`/api/projects/${project.id}/issues`, {
      method: 'POST',
      body: { title: 'First ticket' },
    })
    expect(issue.issueNumber).toBe(1)
    expect(issue.status).toBe('OPEN')

    const issues = await $fetch(`/api/projects/${project.id}/issues`)
    expect(issues).toHaveLength(1)

    const updated = await $fetch(`/api/issues/${issue.id}/status`, {
      method: 'PATCH',
      body: { status: 'IN_PROGRESS' },
    })
    expect(updated.status).toBe('IN_PROGRESS')

    const document = await $fetch(`/api/issues/${issue.id}/documents`, {
      method: 'POST',
      body: { projectId: project.id, issueId: issue.id, kind: 'PLAN', title: 'Plan', content: 'v1' },
    })
    expect(document.latestRevision.revisionNumber).toBe(1)
    expect(document.latestRevision.approvedAt).toBeNull()

    const revision2 = await $fetch(`/api/documents/${document.id}/revisions`, {
      method: 'POST',
      body: { content: 'v2' },
    })
    expect(revision2.revisionNumber).toBe(2)

    const approverId = await insertTestUser()
    const approved = await $fetch(`/api/document-revisions/${revision2.id}/approve`, {
      method: 'POST',
      body: { approvedByUserId: approverId },
    })
    expect(approved.approvedAt).not.toBeNull()

    const notifications = await $fetch(`/api/projects/${project.id}/notifications`)
    expect(Array.isArray(notifications)).toBe(true)

    const unread = await $fetch(`/api/projects/${project.id}/notifications/unread-count`)
    expect(typeof unread.count).toBe('number')
  })
})
