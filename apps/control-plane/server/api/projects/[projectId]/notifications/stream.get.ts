import { and, asc, eq, gt } from 'drizzle-orm'
import { defineEventHandler, getHeader, getRouterParam, sendStream, setResponseHeaders } from 'h3'
import { controlPlane } from '@agentic-worker/db'
import { getDb } from '../../../../utils/db.js'
import { requireProjectRole } from '../../../../utils/auth-guard.js'
import type { NotificationView } from '../../../../utils/notification-service.js'

const KEEP_ALIVE_INTERVAL_MS = 15000

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
 * Internal-only replay query: selects the full row - including the internal bigserial `id` -
 * so the SSE `id:` line can carry it for the browser's Last-Event-ID echo, without adding the
 * internal id to Task 5's public NotificationView contract.
 */
async function listNotificationsAfter(projectId: string, afterId: bigint) {
  return getDb().select().from(controlPlane.notifications)
    .where(and(
      eq(controlPlane.notifications.projectId, projectId),
      gt(controlPlane.notifications.id, afterId),
    ))
    .orderBy(asc(controlPlane.notifications.id))
}

/** A valid cursor is a non-negative integer decimal string; anything else is unrecoverable. */
function isValidCursor(lastEventId: string): boolean {
  return /^\d+$/.test(lastEventId)
}

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  await requireProjectRole(event, projectId, 'MEMBER')
  const lastEventId = getHeader(event, 'last-event-id')

  setResponseHeaders(event, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
  })

  const encoder = new TextEncoder()

  const stream = new ReadableStream({
    async start(controller) {
      if (lastEventId !== undefined) {
        if (isValidCursor(lastEventId)) {
          const rows = await listNotificationsAfter(projectId, BigInt(lastEventId))
          for (const row of rows) {
            const view = toNotificationView(row)
            controller.enqueue(encoder.encode(
              `event: notification.created\nid: ${row.id.toString()}\ndata: ${JSON.stringify(view)}\n\n`,
            ))
          }
        } else {
          // Unrecoverable cursor (NaN/negative/non-integer) - tell the client to reload its
          // full inbox from GET .../notifications rather than trusting a broken cursor.
          controller.enqueue(encoder.encode('event: reset\ndata: {}\n\n'))
        }
      }
      // No Last-Event-ID at all: connect-with-no-cursor. This task only replays
      // since-last-seen, not full history - keep-alive only in that case.

      const keepAlive = setInterval(() => {
        controller.enqueue(encoder.encode(': keep-alive\n\n'))
      }, KEEP_ALIVE_INTERVAL_MS)

      const stopKeepAlive = () => {
        clearInterval(keepAlive)
        try {
          controller.close()
        } catch {
          // already closed
        }
      }
      event.node.req.on('close', stopKeepAlive)
    },
  })

  return sendStream(event, stream)
})
