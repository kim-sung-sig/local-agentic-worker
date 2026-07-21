import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { approveRevision } from '../../../utils/document-service.js'

const ApproveRevisionSchema = z.object({
  approvedByUserId: z.string().min(1),
})

export default defineEventHandler(async (event) => {
  const revisionId = getRouterParam(event, 'revisionId')!
  const parsed = ApproveRevisionSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid approval' })
  }
  const approved = await approveRevision(revisionId, parsed.data.approvedByUserId)
  if (!approved) {
    throw createError({ statusCode: 404, statusMessage: 'Document revision not found' })
  }
  return approved
})
