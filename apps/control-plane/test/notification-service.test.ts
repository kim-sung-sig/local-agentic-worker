import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { listNotifications, unreadCount, markRead } from '../server/utils/notification-service.js'
import { closeDb } from '../server/utils/db.js'
import { startTestDatabase, stopTestDatabase, type TestDatabase } from './support/postgres.js'

let testDb: TestDatabase

async function insertTestProject(): Promise<string> {
  const result = await testDb.pool.query(
    `INSERT INTO control_plane.projects (name, repository_uri) VALUES ($1, $2) RETURNING id`,
    [`Project ${Math.random()}`, `https://github.com/acme/${Math.random()}.git`],
  )
  return result.rows[0].id
}

interface InsertedNotification {
  notificationId: string
  internalId: bigint
}

async function insertTestNotification(
  projectId: string,
  overrides: { title?: string, readAt?: Date | null } = {},
): Promise<InsertedNotification> {
  const result = await testDb.pool.query(
    `INSERT INTO control_plane.notifications
      (event_key, project_id, type, severity, title, message, read_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING id, notification_id`,
    [
      `event-${Math.random()}`,
      projectId,
      'ISSUE_CREATED',
      'INFO',
      overrides.title ?? 'Notification',
      null,
      overrides.readAt ?? null,
    ],
  )
  return { notificationId: result.rows[0].notification_id, internalId: BigInt(result.rows[0].id) }
}

beforeAll(async () => {
  testDb = await startTestDatabase()
  await testDb.pool.query('CREATE SCHEMA IF NOT EXISTS control_plane')
  await testDb.pool.query(`CREATE TABLE control_plane.projects (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    local_path text,
    base_branch text NOT NULL DEFAULT 'main',
    repository_uri text,
    credential_ref text,
    created_at timestamptz NOT NULL DEFAULT now()
  )`)
  await testDb.pool.query(`CREATE UNIQUE INDEX projects_repository_uri_unique
    ON control_plane.projects (repository_uri) WHERE repository_uri IS NOT NULL`)
  await testDb.pool.query(`CREATE TABLE control_plane.notifications (
    id bigserial PRIMARY KEY,
    notification_id uuid NOT NULL DEFAULT gen_random_uuid(),
    event_key text NOT NULL,
    project_id uuid NOT NULL REFERENCES control_plane.projects(id),
    workflow_run_id uuid,
    type text NOT NULL,
    severity text NOT NULL,
    publisher text,
    title text NOT NULL,
    message text,
    read_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(notification_id),
    UNIQUE(event_key)
  )`)
  await testDb.pool.query(`CREATE INDEX notifications_cursor_idx ON control_plane.notifications (project_id, id)`)
  await testDb.pool.query(`CREATE INDEX notifications_unread_idx ON control_plane.notifications (project_id, read_at, id)`)
}, 60_000)

afterAll(async () => {
  await closeDb()
  await stopTestDatabase(testDb)
})

describe('listNotifications / unreadCount / markRead', () => {
  it('lists notifications for a project ordered oldest first, respects afterId cursor', async () => {
    const projectId = await insertTestProject()
    const first = await insertTestNotification(projectId, { title: 'First' })
    await insertTestNotification(projectId, { title: 'Second' })

    const all = await listNotifications(projectId)
    expect(all.map((n) => n.title)).toEqual(['First', 'Second'])

    const afterFirst = await listNotifications(projectId, { afterId: first.internalId })
    expect(afterFirst.map((n) => n.title)).toEqual(['Second'])
  })

  it('counts only unread notifications for the project', async () => {
    const projectId = await insertTestProject()
    await insertTestNotification(projectId, { readAt: null })
    await insertTestNotification(projectId, { readAt: new Date() })

    expect(await unreadCount(projectId)).toBe(1)
  })

  it('marks the given notification ids read and returns the count actually changed', async () => {
    const projectId = await insertTestProject()
    const n = await insertTestNotification(projectId, { readAt: null })

    const changed = await markRead(projectId, [n.notificationId])
    expect(changed).toBe(1)
    expect(await unreadCount(projectId)).toBe(0)
  })

  it('rejects marking more than 100 notification ids at once', async () => {
    const projectId = await insertTestProject()
    const tooMany = Array.from({ length: 101 }, () => '00000000-0000-0000-0000-000000000000')
    await expect(markRead(projectId, tooMany)).rejects.toThrow()
  })
})
