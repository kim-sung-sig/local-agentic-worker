import { defineEventHandler, getRouterParam, createError } from 'h3'
import { getIssue } from '../../../utils/issue-service.js'

export default defineEventHandler(async (event) => {
  const issue = await getIssue(getRouterParam(event, 'issueId')!)
  if (!issue) throw createError({ statusCode: 404, statusMessage: 'Issue not found' })
  return issue
})
