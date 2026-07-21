import { defineEventHandler, getQuery, getRouterParam } from 'h3'
import { listNotifications } from '../../../../utils/notification-service.js'
import { requireProjectRole } from '../../../../utils/auth-guard.js'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  await requireProjectRole(event, projectId, 'MEMBER')
  const query = getQuery(event)
  const afterId = typeof query.afterId === 'string' ? BigInt(query.afterId) : undefined
  return listNotifications(projectId, { afterId })
})
