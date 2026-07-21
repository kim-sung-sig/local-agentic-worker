import { defineEventHandler, getRouterParam, createError } from 'h3'
import { getProject } from '../../../utils/project-service.js'
import { requireProjectRole } from '../../../utils/auth-guard.js'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  await requireProjectRole(event, projectId, 'MEMBER')
  const project = await getProject(projectId)
  if (!project) {
    throw createError({ statusCode: 404, statusMessage: 'Project not found' })
  }
  return project
})
