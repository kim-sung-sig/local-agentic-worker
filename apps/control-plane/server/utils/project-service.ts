import { eq } from 'drizzle-orm'
import { controlPlane } from '@agentic-worker/db'
import { getDb } from './db.js'

export interface RegisterProjectInput {
  name: string
  repositoryUri: string
  baseBranch?: string
  credentialRef?: string
}

export interface ProjectView {
  id: string
  name: string
  repositoryUri: string | null
  baseBranch: string
  createdAt: string
}

function toView(row: typeof controlPlane.projects.$inferSelect): ProjectView {
  return {
    id: row.id,
    name: row.name,
    repositoryUri: row.repositoryUri,
    baseBranch: row.baseBranch,
    createdAt: row.createdAt.toISOString(),
  }
}

export async function registerProject(input: RegisterProjectInput, ownerUserId: string): Promise<ProjectView> {
  return getDb().transaction(async (tx) => {
    const [row] = await tx.insert(controlPlane.projects).values({
      name: input.name,
      repositoryUri: input.repositoryUri,
      baseBranch: input.baseBranch ?? 'main',
      credentialRef: input.credentialRef ?? null,
    }).returning()
    if (!row) {
      throw new Error('registerProject: insert returned no row')
    }
    await tx.insert(controlPlane.memberships).values({
      userId: ownerUserId,
      projectId: row.id,
      role: 'OWNER',
    })
    return toView(row)
  })
}

export async function listProjects(userId: string): Promise<ProjectView[]> {
  const rows = await getDb().select({ project: controlPlane.projects })
    .from(controlPlane.projects)
    .innerJoin(controlPlane.memberships, eq(controlPlane.memberships.projectId, controlPlane.projects.id))
    .where(eq(controlPlane.memberships.userId, userId))
  return rows.map(({ project }) => toView(project))
}

export async function getProject(projectId: string): Promise<ProjectView | null> {
  const [row] = await getDb().select().from(controlPlane.projects).where(eq(controlPlane.projects.id, projectId))
  return row ? toView(row) : null
}
