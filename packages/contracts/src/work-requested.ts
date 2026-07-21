import { z } from 'zod'

/** Matches java.util.UUID.fromString() acceptance - no RFC version/variant nibble enforcement. */
const looseUuid = z.string().regex(
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/,
  { message: 'must be a UUID' },
)

/** A repository URI must be remote (https/http/ssh) - never a filesystem path. */
const remoteRepositoryUri = z.string().url().refine(
  (value) => /^(https|http|ssh):\/\//.test(value),
  { message: 'repositoryUri must be https, http, or ssh' },
)

export const WorkRequestedSchema = z.object({
  issueId: looseUuid,
  projectId: looseUuid,
  repositoryUri: remoteRepositoryUri,
  baseBranch: z.string().min(1),
  rawSpecification: z.string().min(1),
  occurredAt: z.string().datetime(),
})

export type WorkRequested = z.infer<typeof WorkRequestedSchema>

export function workflowIdFor(issueId: string): string {
  return `issue-${issueId}`
}
