import { z } from 'zod'
import { defineEventHandler, readBody, getRouterParam, createError } from 'h3'
import { createDocument } from '../../../../utils/document-service.js'
import { getIssue } from '../../../../utils/issue-service.js'
import { requireProjectRole } from '../../../../utils/auth-guard.js'

const CreateDocumentSchema = z.object({
  projectId: z.string().min(1),
  kind: z.enum([
    'PROMPT_TEMPLATE',
    'DEVELOPMENT_GUIDE',
    'QA_GUIDE',
    'PLAN',
    'IMPLEMENTATION_PLAN',
    'DEVELOPMENT_RESULT',
    'QA_REPORT',
  ]),
  title: z.string().min(1),
  content: z.string(),
})

export default defineEventHandler(async (event) => {
  const issueId = getRouterParam(event, 'issueId')!
  const issue = await getIssue(issueId)
  if (!issue) {
    throw createError({ statusCode: 404, statusMessage: 'Issue not found' })
  }
  await requireProjectRole(event, issue.projectId, 'MEMBER')
  const parsed = CreateDocumentSchema.safeParse(await readBody(event))
  if (!parsed.success) {
    throw createError({ statusCode: 400, statusMessage: parsed.error.issues[0]?.message ?? 'Invalid document' })
  }
  return createDocument({ ...parsed.data, issueId })
})
