import { randomUUID } from 'node:crypto'
import { desc, eq } from 'drizzle-orm'
import { controlPlane } from '@agentic-worker/db'
import { getDb } from './db.js'
import { withOutbox } from './outbox.js'

export interface CreateIssueInput {
  title: string
  description?: string
  priority?: string
}

export interface IssueView {
  id: string
  projectId: string
  issueNumber: number
  title: string
  description: string | null
  priority: string | null
  status: string
  createdAt: string
}

function toView(row: typeof controlPlane.issues.$inferSelect): IssueView {
  return {
    id: row.id,
    projectId: row.projectId,
    issueNumber: row.issueNumber,
    title: row.title,
    description: row.description,
    priority: row.priority,
    status: row.status,
    createdAt: row.createdAt.toISOString(),
  }
}

async function nextIssueNumber(projectId: string): Promise<number> {
  const [last] = await getDb().select({ issueNumber: controlPlane.issues.issueNumber })
    .from(controlPlane.issues)
    .where(eq(controlPlane.issues.projectId, projectId))
    .orderBy(desc(controlPlane.issues.issueNumber))
    .limit(1)
  return (last?.issueNumber ?? 0) + 1
}

/**
 * Inserts the issue and its ISSUE_CREATED outbox row in one transaction. The id is
 * generated client-side so it can be used as the outbox event's `aggregateId` before
 * the row exists (Drizzle's `.returning()` result isn't available until after insert).
 */
async function attemptCreate(projectId: string, input: CreateIssueInput): Promise<IssueView> {
  const id = randomUUID()
  const issueNumber = await nextIssueNumber(projectId)

  return withOutbox(
    getDb(),
    {
      aggregateType: 'issue',
      aggregateId: id,
      eventType: 'ISSUE_CREATED',
      payload: { projectId, issueNumber, title: input.title },
    },
    async (tx) => {
      const [row] = await tx.insert(controlPlane.issues).values({
        id,
        projectId,
        issueNumber,
        title: input.title,
        description: input.description ?? null,
        priority: input.priority ?? null,
      }).returning()
      if (!row) {
        throw new Error('createIssue: insert returned no row')
      }
      return toView(row)
    },
  )
}

export async function createIssue(projectId: string, input: CreateIssueInput): Promise<IssueView> {
  try {
    return await attemptCreate(projectId, input)
  } catch (error: any) {
    // Unique-violation race on (project_id, issue_number) - recompute the max and retry once.
    if (error?.code === '23505') {
      return attemptCreate(projectId, input)
    }
    throw error
  }
}

export async function listIssuesByProject(projectId: string): Promise<IssueView[]> {
  const rows = await getDb().select().from(controlPlane.issues).where(eq(controlPlane.issues.projectId, projectId))
  return rows.map(toView)
}

export async function getIssue(issueId: string): Promise<IssueView | null> {
  const [row] = await getDb().select().from(controlPlane.issues).where(eq(controlPlane.issues.id, issueId))
  return row ? toView(row) : null
}

export async function updateIssueStatus(issueId: string, status: string): Promise<IssueView | null> {
  const [row] = await getDb().update(controlPlane.issues).set({ status })
    .where(eq(controlPlane.issues.id, issueId)).returning()
  return row ? toView(row) : null
}
