import { index, text, timestamp, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
import { documents } from './documents.js'
import { documentRevisions } from './document-revisions.js'

/** A stored artifact (e.g. a file blob or external ref) attached to a document revision. */
export const documentArtifacts = controlPlaneSchema.table('document_artifacts', {
  id: uuid('id').primaryKey().defaultRandom(),
  documentId: uuid('document_id').notNull().references(() => documents.id),
  documentRevisionId: uuid('document_revision_id').references(() => documentRevisions.id),
  artifactRef: text('artifact_ref').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  index('document_artifacts_document_id_idx').on(table.documentId),
])
