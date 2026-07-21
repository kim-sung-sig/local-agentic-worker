import { and, asc, eq, gt, inArray, isNull, sql } from 'drizzle-orm'
import { createError } from 'h3'
import { controlPlane } from '@agentic-worker/db'
import { getDb } from './db.js'

const MAX_MARK_READ_IDS = 100

export interface NotificationView {
  notificationId: string
  eventKey: string
  type: string
  severity: string
  title: string
  message: string | null
  readAt: string | null
  createdAt: string
}

export interface ListNotificationsOptions {
  afterId?: bigint
  limit?: number
}

function toNotificationView(row: typeof controlPlane.notifications.$inferSelect): NotificationView {
  return {
    notificationId: row.notificationId,
    eventKey: row.eventKey,
    type: row.type,
    severity: row.severity,
    title: row.title,
    message: row.message,
    readAt: row.readAt ? row.readAt.toISOString() : null,
    createdAt: row.createdAt.toISOString(),
  }
}

/**
 * Lists a project's notifications oldest-first by the internal bigserial `id`, which is
 * never exposed in NotificationView. `opts.afterId` restricts to `id > afterId` - this is
 * the cursor Task 6's SSE Last-Event-ID replay is built on.
 */
export async function listNotifications(
  projectId: string,
  opts?: ListNotificationsOptions,
): Promise<NotificationView[]> {
  const conditions = [eq(controlPlane.notifications.projectId, projectId)]
  if (opts?.afterId !== undefined) {
    conditions.push(gt(controlPlane.notifications.id, opts.afterId))
  }

  let query = getDb().select().from(controlPlane.notifications)
    .where(and(...conditions))
    .orderBy(asc(controlPlane.notifications.id))
    .$dynamic()
  if (opts?.limit !== undefined) {
    query = query.limit(opts.limit)
  }

  const rows = await query
  return rows.map(toNotificationView)
}

/** Counts a project's notifications that have not yet been read. */
export async function unreadCount(projectId: string): Promise<number> {
  const [row] = await getDb().select({ count: sql<string>`count(*)` })
    .from(controlPlane.notifications)
    .where(and(
      eq(controlPlane.notifications.projectId, projectId),
      isNull(controlPlane.notifications.readAt),
    ))
  return Number(row?.count ?? 0)
}

/**
 * Marks the given notification ids read for the project and returns the count actually
 * changed. Rejects with a 400 when more than 100 ids are given, matching the Java
 * NotificationCommandService.markRead limit.
 */
export async function markRead(projectId: string, notificationIds: string[]): Promise<number> {
  if (notificationIds.length > MAX_MARK_READ_IDS) {
    throw createError({ statusCode: 400, statusMessage: `Cannot mark more than ${MAX_MARK_READ_IDS} notifications read at once` })
  }
  if (notificationIds.length === 0) {
    return 0
  }

  const rows = await getDb().update(controlPlane.notifications)
    .set({ readAt: new Date() })
    .where(and(
      eq(controlPlane.notifications.projectId, projectId),
      inArray(controlPlane.notifications.notificationId, notificationIds),
      isNull(controlPlane.notifications.readAt),
    ))
    .returning({ id: controlPlane.notifications.id })
  return rows.length
}
