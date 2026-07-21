import { text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { users } from './users.js'
import { projects } from './projects.js'

/** Grants a user a role on a project. Both FKs stay within control_plane. */
export const memberships = controlPlaneSchema.table('memberships', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id').notNull().references(() => users.id),
  projectId: uuid('project_id').notNull().references(() => projects.id),
  role: text('role').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  unique('memberships_user_id_project_id_unique').on(table.userId, table.projectId),
])
