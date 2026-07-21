import { index, text, timestamp, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { projects } from './projects.js'
import { issues } from './issues.js'

/** Project-scoped reusable guidance or issue-scoped generated artifacts. */
export const documentKindEnum = controlPlaneSchema.enum('document_kind', [
  'PROMPT_TEMPLATE',
  'DEVELOPMENT_GUIDE',
  'QA_GUIDE',
  'PLAN',
  'IMPLEMENTATION_PLAN',
  'DEVELOPMENT_RESULT',
  'QA_REPORT',
])

/** A document header; body content lives in append-only document_revisions rows. */
export const documents = controlPlaneSchema.table('documents', {
  id: uuid('id').primaryKey().defaultRandom(),
  projectId: uuid('project_id').notNull().references(() => projects.id),
  issueId: uuid('issue_id').references(() => issues.id),
  kind: documentKindEnum('kind').notNull(),
  title: text('title').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  index('documents_project_id_idx').on(table.projectId),
])
