import { z } from 'zod'
import { defineEventHandler, readBody, createError } from 'h3'
import { registerProject } from '../../utils/project-service.js'
import { requireSession } from '../../utils/auth-guard.js'

const remoteRepositoryUri = z.string().url().refine(
  (value) => /^(https|http|ssh):\/\//.test(value),
  { message: 'repositoryUri must be https, http, or ssh' },
)

const RegisterProjectSchema = z.object({
  name: z.string().min(1),
  repositoryUri: remoteRepositoryUri,
  baseBranch: z.string().min(1).optional(),
  credentialRef: z.string().min(1).optional(),
})

export default defineEventHandler(async (event) => {
  const user = await requireSession(event)
  const body = await readBody(event)
  const parsed = RegisterProjectSchema.safeParse(body)
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid project' })
  }
  return registerProject(parsed.data, user.id)
})
