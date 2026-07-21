import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { addRevision, getDocumentProjectId } from '../../../../utils/document-service.js'
import { requireProjectRole } from '../../../../utils/auth-guard.js'

const AddRevisionSchema = z.object({
  content: z.string(),
})

export default defineEventHandler(async (event) => {
  const documentId = getRouterParam(event, 'documentId')!
  const projectId = await getDocumentProjectId(documentId)
  if (!projectId) {
    throw createError({ statusCode: 404, statusMessage: 'Document not found' })
  }
  await requireProjectRole(event, projectId, 'MEMBER')

  const parsed = AddRevisionSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid revision' })
  }
  return addRevision(documentId, parsed.data.content)
})
