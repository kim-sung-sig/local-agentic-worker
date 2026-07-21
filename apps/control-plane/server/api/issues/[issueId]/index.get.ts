import { defineEventHandler, getRouterParam, createError } from 'h3'
import { getIssue } from '../../../utils/issue-service.js'
import { requireProjectRole } from '../../../utils/auth-guard.js'

export default defineEventHandler(async (event) => {
  const issue = await getIssue(getRouterParam(event, 'issueId')!)
  if (!issue) throw createError({ statusCode: 404, statusMessage: 'Issue not found' })
  await requireProjectRole(event, issue.projectId, 'MEMBER')
  return issue
})
