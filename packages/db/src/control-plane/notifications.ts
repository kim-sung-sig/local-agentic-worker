import { bigserial, index, text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { projects } from './projects.js'

/**
 * A project-scoped notification. workflow_run_id is a plain uuid (no FK) because
 * engine.workflow_runs lives in a different schema and cross-schema FKs are forbidden.
 */
export const notifications = controlPlaneSchema.table('notifications', {
  id: bigserial('id', { mode: 'bigint' }).primaryKey(),
  notificationId: uuid('notification_id').notNull().defaultRandom(),
  eventKey: text('event_key').notNull(),
  projectId: uuid('project_id').notNull().references(() => projects.id),
  workflowRunId: uuid('workflow_run_id'),
  type: text('type').notNull(),
  severity: text('severity').notNull(),
  publisher: text('publisher'),
  title: text('title').notNull(),
  message: text('message'),
  readAt: timestamp('read_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  unique('notifications_notification_id_unique').on(table.notificationId),
  unique('notifications_event_key_unique').on(table.eventKey),
  index('notifications_cursor_idx').on(table.projectId, table.id),
  index('notifications_unread_idx').on(table.projectId, table.readAt, table.id),
])
