import { index, integer, text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { projects } from './projects.js'

/** A ticket filed against a project. FK to projects stays within control_plane. */
export const issues = controlPlaneSchema.table('issues', {
  id: uuid('id').primaryKey().defaultRandom(),
  projectId: uuid('project_id').notNull().references(() => projects.id),
  issueNumber: integer('issue_number').notNull(),
  title: text('title').notNull(),
  description: text('description'),
  priority: text('priority'),
  status: text('status').notNull().default('OPEN'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  unique('issues_project_id_issue_number_unique').on(table.projectId, table.issueNumber),
  index('issues_project_id_idx').on(table.projectId),
])
