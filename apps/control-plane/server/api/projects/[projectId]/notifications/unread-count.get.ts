import { defineEventHandler, getRouterParam } from 'h3'
import { unreadCount } from '../../../../utils/notification-service.js'
import { requireProjectRole } from '../../../../utils/auth-guard.js'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  await requireProjectRole(event, projectId, 'MEMBER')
  return { count: await unreadCount(projectId) }
})
