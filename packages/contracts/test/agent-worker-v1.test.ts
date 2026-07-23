import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { ExecutionEventSchema, ExecutionStatusSchema, ExecutionSubmissionSchema, WorkerCapabilitiesSchema } from '../src/agent-worker-v1.js'

const fixture = (name: string) => JSON.parse(readFileSync(new URL(`./fixtures/agent-worker-v1/${name}`, import.meta.url), 'utf8')) as unknown

const validSubmission = {
  contractVersion: 'agent-worker/v1', idempotencyKey: 'run-1:QA:2:1', workflowRunId: 'run-1', stage: 'QA', attemptNumber: 2, stageExecutionGeneration: 1,
  adapterId: 'codex-cli-python', mode: 'READ', project: { projectId: 'project-1', repositoryUri: 'https://github.com/acme/project.git', baseBranch: 'main', credentialRef: 'credential-1', requestedSourceCommit: null },
}

describe('agent-worker/v1 submission', () => {
  it('accepts the versioned safe payload', () => {
    expect(ExecutionSubmissionSchema.safeParse(validSubmission).success).toBe(true)
    expect(ExecutionSubmissionSchema.safeParse(fixture('valid-submission.json')).success).toBe(true)
  })

  it('rejects local workspace references and invalid idempotency keys', () => {
    expect(ExecutionSubmissionSchema.safeParse({ ...validSubmission, workspaceRef: 'C:\\secret' }).success).toBe(false)
    expect(ExecutionSubmissionSchema.safeParse({ ...validSubmission, idempotencyKey: 'wrong' }).success).toBe(false)
    expect(ExecutionSubmissionSchema.safeParse({ ...validSubmission, project: { ...validSubmission.project, repositoryUri: 'file:///C:/secret' } }).success).toBe(false)
    expect(ExecutionSubmissionSchema.safeParse({ ...validSubmission, project: { ...validSubmission.project, repositoryUri: 'https://token:password@github.com/acme/project.git' } }).success).toBe(false)
    expect(ExecutionSubmissionSchema.safeParse({ ...validSubmission, project: { ...validSubmission.project, baseBranch: '\\\\server\\share' } }).success).toBe(false)
    expect(ExecutionSubmissionSchema.safeParse({ ...validSubmission, project: { ...validSubmission.project, baseBranch: '\\Windows\\secret' } }).success).toBe(false)
  })

  it('validates terminal, event, and capabilities fixtures', () => {
    expect(ExecutionStatusSchema.safeParse(fixture('terminal-execution.json')).success).toBe(true)
    expect(WorkerCapabilitiesSchema.safeParse(fixture('capabilities.json')).success).toBe(true)
    expect(ExecutionEventSchema.array().safeParse(fixture('ordered-events.json')).success).toBe(true)
  })
})
