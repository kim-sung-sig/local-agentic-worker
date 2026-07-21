import { integer, text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { documents } from './documents.js'

/**
 * Append-only content revisions for a document. Edits create a new row with an
 * incremented revision_number; existing rows are never updated (immutable).
 */
export const documentRevisions = controlPlaneSchema.table('document_revisions', {
  id: uuid('id').primaryKey().defaultRandom(),
  documentId: uuid('document_id').notNull().references(() => documents.id),
  revisionNumber: integer('revision_number').notNull(),
  content: text('content').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  unique('document_revisions_document_id_revision_number_unique')
    .on(table.documentId, table.revisionNumber),
])
