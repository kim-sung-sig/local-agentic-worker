import { defineEventHandler, getRouterParam, createError } from 'h3'
import { getProject } from '../../../utils/project-service.js'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  const project = await getProject(projectId)
  if (!project) {
    throw createError({ statusCode: 404, statusMessage: 'Project not found' })
  }
  return project
})
