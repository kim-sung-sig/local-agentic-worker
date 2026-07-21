import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { addRevision } from '../../../../utils/document-service.js'

const AddRevisionSchema = z.object({
  content: z.string(),
})

export default defineEventHandler(async (event) => {
  const documentId = getRouterParam(event, 'documentId')!
  const parsed = AddRevisionSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid revision' })
  }
  return addRevision(documentId, parsed.data.content)
})
