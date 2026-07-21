import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { markRead } from '../../../../utils/notification-service.js'
import { requireProjectRole } from '../../../../utils/auth-guard.js'

const MarkReadSchema = z.object({
  notificationIds: z.array(z.string()).min(1),
})

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  await requireProjectRole(event, projectId, 'MEMBER')
  const parsed = MarkReadSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid request' })
  }
  const changed = await markRead(projectId, parsed.data.notificationIds)
  return { changed }
})
