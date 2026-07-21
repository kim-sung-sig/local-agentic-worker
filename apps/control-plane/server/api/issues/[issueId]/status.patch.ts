import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { getIssue, updateIssueStatus } from '../../../utils/issue-service.js'
import { requireProjectRole } from '../../../utils/auth-guard.js'

const UpdateStatusSchema = z.object({ status: z.string().min(1) })

export default defineEventHandler(async (event) => {
  const issueId = getRouterParam(event, 'issueId')!
  const issue = await getIssue(issueId)
  if (!issue) throw createError({ statusCode: 404, statusMessage: 'Issue not found' })
  await requireProjectRole(event, issue.projectId, 'MEMBER')

  const parsed = UpdateStatusSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'status is required' })
  }
  const updated = await updateIssueStatus(issueId, parsed.data.status)
  if (!updated) throw createError({ statusCode: 404, statusMessage: 'Issue not found' })
  return updated
})
