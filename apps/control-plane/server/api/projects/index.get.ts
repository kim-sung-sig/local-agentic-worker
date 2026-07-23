import { defineEventHandler } from 'h3'
import { listProjects } from '../../utils/project-service.js'
import { requireSession } from '../../utils/auth-guard.js'

export default defineEventHandler(async (event) => {
  const user = await requireSession(event)
  return listProjects(user.id)
})
