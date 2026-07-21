import { jsonb, text, timestamp, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'

/** Transactional outbox row: written in the same DB transaction as its triggering change. */
export const outboxEvents = controlPlaneSchema.table('outbox_events', {
  id: uuid('id').primaryKey().defaultRandom(),
  aggregateType: text('aggregate_type').notNull(),
  aggregateId: uuid('aggregate_id').notNull(),
  eventType: text('event_type').notNull(),
  payload: jsonb('payload').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  processedAt: timestamp('processed_at', { withTimezone: true }),
})
