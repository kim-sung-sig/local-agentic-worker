import { z } from 'zod'

export const AGENT_WORKER_V1 = 'agent-worker/v1' as const

const StageSchema = z.enum(['INTAKE', 'PLANNING', 'IMPLEMENTATION', 'QA'])
const ModeSchema = z.enum(['READ', 'WRITE'])
const absolutePath = /^(?:[A-Za-z]:[\\/]|[\\/]{1,2}|file:\/\/)/i
const forbiddenKey = /^(?:token|password|secret|apiKey)$/i

const RemoteRepositoryUriSchema = z.string().url().refine((value) => {
  const uri = new URL(value)
  return uri.protocol !== 'file:' && !uri.username && !uri.password && !uri.search && !uri.hash
}, 'must be a credential-free remote repository URI')

function hasUnsafeValue(value: unknown): boolean {
  if (typeof value === 'string') return absolutePath.test(value)
  if (Array.isArray(value)) return value.some(hasUnsafeValue)
  if (value && typeof value === 'object') {
    return Object.entries(value).some(([key, nested]) => forbiddenKey.test(key) || hasUnsafeValue(nested))
  }
  return false
}

export const ProjectExecutionSnapshotSchema = z.object({
  projectId: z.string().min(1),
  repositoryUri: RemoteRepositoryUriSchema,
  baseBranch: z.string().min(1),
  credentialRef: z.string().min(1).nullable(),
  requestedSourceCommit: z.string().min(1).nullable(),
}).strict()

export const ExecutionSubmissionSchema = z.object({
  contractVersion: z.literal(AGENT_WORKER_V1),
  idempotencyKey: z.string().min(1),
  workflowRunId: z.string().min(1),
  stage: StageSchema,
  attemptNumber: z.number().int().positive(),
  stageExecutionGeneration: z.number().int().positive(),
  adapterId: z.string().min(1),
  project: ProjectExecutionSnapshotSchema,
  mode: ModeSchema,
}).strict().superRefine((submission, context) => {
  const expected = `${submission.workflowRunId}:${submission.stage}:${submission.attemptNumber}:${submission.stageExecutionGeneration}`
  if (submission.idempotencyKey !== expected) {
    context.addIssue({ code: 'custom', path: ['idempotencyKey'], message: 'must match workflowRunId:stage:attemptNumber:stageExecutionGeneration' })
  }
  if (hasUnsafeValue(submission)) {
    context.addIssue({ code: 'custom', message: 'submission contains a local path or secret-like key' })
  }
})

export const ExecutionStatusSchema = z.object({
  executionId: z.string().min(1),
  status: z.enum(['ACCEPTED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED']),
  terminal: z.boolean(),
  artifactRefs: z.array(z.string()).default([]),
}).strict()

export const ExecutionSubmissionResultSchema = z.object({
  executionId: z.string().min(1),
}).strict()

export const ExecutionEventSchema = z.object({
  executionId: z.string().min(1),
  cursor: z.number().int().positive(),
  type: z.enum(['accepted', 'running', 'completed', 'failed', 'cancelled']),
  data: z.record(z.string(), z.unknown()).default({}),
}).strict()

export const WorkerCapabilitiesSchema = z.object({
  workerId: z.string().min(1),
  adapterIds: z.array(z.string().min(1)),
  modes: z.array(ModeSchema),
}).strict()

export type ProjectExecutionSnapshot = z.infer<typeof ProjectExecutionSnapshotSchema>
export type ExecutionSubmission = z.infer<typeof ExecutionSubmissionSchema>
export type ExecutionStatus = z.infer<typeof ExecutionStatusSchema>
export type ExecutionSubmissionResult = z.infer<typeof ExecutionSubmissionResultSchema>
export type ExecutionEvent = z.infer<typeof ExecutionEventSchema>
export type WorkerCapabilities = z.infer<typeof WorkerCapabilitiesSchema>
