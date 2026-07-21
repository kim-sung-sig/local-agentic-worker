import { desc, eq } from 'drizzle-orm'
import { controlPlane } from '@agentic-worker/db'
import { getDb } from './db.js'
import { withOutbox } from './outbox.js'

export interface CreateDocumentInput {
  projectId: string
  issueId?: string
  kind: typeof controlPlane.documentKindEnum.enumValues[number]
  title: string
  content: string
}

export interface DocumentRevisionView {
  id: string
  documentId: string
  revisionNumber: number
  content: string
  approvedAt: string | null
  approvedByUserId: string | null
  createdAt: string
}

export interface DocumentView {
  id: string
  projectId: string
  issueId: string | null
  kind: string
  title: string
  createdAt: string
  latestRevision: DocumentRevisionView
}

function toRevisionView(row: typeof controlPlane.documentRevisions.$inferSelect): DocumentRevisionView {
  return {
    id: row.id,
    documentId: row.documentId,
    revisionNumber: row.revisionNumber,
    content: row.content,
    approvedAt: row.approvedAt ? row.approvedAt.toISOString() : null,
    approvedByUserId: row.approvedByUserId,
    createdAt: row.createdAt.toISOString(),
  }
}

function toDocumentView(
  row: typeof controlPlane.documents.$inferSelect,
  latestRevision: DocumentRevisionView,
): DocumentView {
  return {
    id: row.id,
    projectId: row.projectId,
    issueId: row.issueId,
    kind: row.kind,
    title: row.title,
    createdAt: row.createdAt.toISOString(),
    latestRevision,
  }
}

/**
 * Inserts the document header and its first revision (revisionNumber 1) in one
 * transaction. Document creation is not an outbox trigger (only approval is), so no
 * outbox event is written here.
 */
export async function createDocument(input: CreateDocumentInput): Promise<DocumentView> {
  return getDb().transaction(async (tx) => {
    const [documentRow] = await tx.insert(controlPlane.documents).values({
      projectId: input.projectId,
      issueId: input.issueId ?? null,
      kind: input.kind,
      title: input.title,
    }).returning()
    if (!documentRow) {
      throw new Error('createDocument: document insert returned no row')
    }

    const [revisionRow] = await tx.insert(controlPlane.documentRevisions).values({
      documentId: documentRow.id,
      revisionNumber: 1,
      content: input.content,
    }).returning()
    if (!revisionRow) {
      throw new Error('createDocument: revision insert returned no row')
    }

    return toDocumentView(documentRow, toRevisionView(revisionRow))
  })
}

/**
 * Appends a new revision at max(revisionNumber)+1. Never updates an existing revision -
 * document_revisions rows are immutable content-wise.
 */
export async function addRevision(documentId: string, content: string): Promise<DocumentRevisionView> {
  const [last] = await getDb().select({ revisionNumber: controlPlane.documentRevisions.revisionNumber })
    .from(controlPlane.documentRevisions)
    .where(eq(controlPlane.documentRevisions.documentId, documentId))
    .orderBy(desc(controlPlane.documentRevisions.revisionNumber))
    .limit(1)
  const nextRevisionNumber = (last?.revisionNumber ?? 0) + 1

  const [row] = await getDb().insert(controlPlane.documentRevisions).values({
    documentId,
    revisionNumber: nextRevisionNumber,
    content,
  }).returning()
  if (!row) {
    throw new Error('addRevision: insert returned no row')
  }
  return toRevisionView(row)
}

/**
 * Approval is a status change on the existing revision row - only approvedAt and
 * approvedByUserId are updated, never content, and no new revision is inserted. Wrapped in
 * withOutbox so the DOCUMENT_REVISION_APPROVED event lands in the same transaction.
 * Returns null for an unknown revision id.
 */
export async function approveRevision(
  revisionId: string,
  approvedByUserId: string,
): Promise<DocumentRevisionView | null> {
  const [existing] = await getDb().select().from(controlPlane.documentRevisions)
    .where(eq(controlPlane.documentRevisions.id, revisionId))
  if (!existing) {
    return null
  }

  const approvedAt = new Date()

  return withOutbox(
    getDb(),
    {
      aggregateType: 'document_revision',
      aggregateId: revisionId,
      eventType: 'DOCUMENT_REVISION_APPROVED',
      payload: {
        documentId: existing.documentId,
        revisionNumber: existing.revisionNumber,
        approvedByUserId,
      },
    },
    async (tx) => {
      const [row] = await tx.update(controlPlane.documentRevisions)
        .set({ approvedAt, approvedByUserId })
        .where(eq(controlPlane.documentRevisions.id, revisionId))
        .returning()
      if (!row) {
        throw new Error('approveRevision: update returned no row')
      }
      return toRevisionView(row)
    },
  )
}
