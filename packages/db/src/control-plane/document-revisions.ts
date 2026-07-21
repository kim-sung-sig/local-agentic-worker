import { integer, text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { documents } from './documents.js'
import { users } from './users.js'

/**
 * Append-only content revisions for a document. Edits create a new row with an
 * incremented revision_number; existing rows are never updated except to record approval
 * (approved_at/approved_by_user_id), which is a status change, not a content edit.
 */
export const documentRevisions = controlPlaneSchema.table('document_revisions', {
  id: uuid('id').primaryKey().defaultRandom(),
  documentId: uuid('document_id').notNull().references(() => documents.id),
  revisionNumber: integer('revision_number').notNull(),
  content: text('content').notNull(),
  approvedAt: timestamp('approved_at', { withTimezone: true }),
  approvedByUserId: uuid('approved_by_user_id').references(() => users.id),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  unique('document_revisions_document_id_revision_number_unique')
    .on(table.documentId, table.revisionNumber),
])
