import { defineEventHandler, getRouterParam } from 'h3'
import { listIssuesByProject } from '../../../../utils/issue-service.js'
import { requireProjectRole } from '../../../../utils/auth-guard.js'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')!
  await requireProjectRole(event, projectId, 'MEMBER')
  return listIssuesByProject(projectId)
})
