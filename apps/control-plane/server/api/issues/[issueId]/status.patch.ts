import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { updateIssueStatus } from '../../../utils/issue-service.js'

const UpdateStatusSchema = z.object({ status: z.string().min(1) })

export default defineEventHandler(async (event) => {
  const parsed = UpdateStatusSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: 'status is required' })
  }
  const updated = await updateIssueStatus(getRouterParam(event, 'issueId')!, parsed.data.status)
  if (!updated) throw createError({ statusCode: 404, statusMessage: 'Issue not found' })
  return updated
})
