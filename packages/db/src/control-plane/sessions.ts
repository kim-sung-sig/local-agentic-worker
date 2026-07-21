import { text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { users } from './users.js'

/** An authenticated session token for a user. */
export const sessions = controlPlaneSchema.table('sessions', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id').notNull().references(() => users.id),
  token: text('token').notNull(),
  expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  unique('sessions_token_unique').on(table.token),
])
