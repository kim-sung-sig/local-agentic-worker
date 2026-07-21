import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { approveRevision, getRevisionProjectId } from '../../../utils/document-service.js'
import { requireProjectRole } from '../../../utils/auth-guard.js'

const ApproveRevisionSchema = z.object({
  approvedByUserId: z.string().min(1),
})

export default defineEventHandler(async (event) => {
  const revisionId = getRouterParam(event, 'revisionId')!
  const projectId = await getRevisionProjectId(revisionId)
  if (!projectId) {
    throw createError({ statusCode: 404, statusMessage: 'Document revision not found' })
  }
  await requireProjectRole(event, projectId, 'MAINTAINER')

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
