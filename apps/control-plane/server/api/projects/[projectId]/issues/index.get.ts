import { defineEventHandler, getRouterParam } from 'h3'
import { listIssuesByProject } from '../../../../utils/issue-service.js'

export default defineEventHandler((event) => listIssuesByProject(getRouterParam(event, 'projectId')!))
