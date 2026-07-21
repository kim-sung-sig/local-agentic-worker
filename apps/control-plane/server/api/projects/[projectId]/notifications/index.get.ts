import { defineEventHandler, getQuery, getRouterParam } from 'h3'
import { listNotifications } from '../../../../utils/notification-service.js'

export default defineEventHandler((event) => {
  const projectId = getRouterParam(event, 'projectId')!
  const query = getQuery(event)
  const afterId = typeof query.afterId === 'string' ? BigInt(query.afterId) : undefined
  return listNotifications(projectId, { afterId })
})
