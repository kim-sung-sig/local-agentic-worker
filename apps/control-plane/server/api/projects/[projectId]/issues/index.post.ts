import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { createIssue } from '../../../../utils/issue-service.js'
import { getProject } from '../../../../utils/project-service.js'
import { requireProjectRole } from '../../../../utils/auth-guard.js'

const CreateIssueSchema = z.object({
  title: z.string().min(1),
  description: z.string().optional(),
  priority: z.string().optional(),
})

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  await requireProjectRole(event, projectId, 'MEMBER')
  if (!(await getProject(projectId))) {
    throw createError({ statusCode: 404, statusMessage: 'Project not found' })
  }
  const parsed = CreateIssueSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid issue' })
  }
  return createIssue(projectId, parsed.data)
})
