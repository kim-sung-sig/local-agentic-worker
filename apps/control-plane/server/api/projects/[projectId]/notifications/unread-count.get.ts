import { defineEventHandler, getRouterParam } from 'h3'
import { unreadCount } from '../../../../utils/notification-service.js'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  return { count: await unreadCount(projectId) }
})
