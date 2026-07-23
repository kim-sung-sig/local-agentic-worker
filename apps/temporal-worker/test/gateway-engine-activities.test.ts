import type { EngineActivities, ExecutionSubmission, ProjectExecutionSnapshot } from '@agentic-worker/contracts'
import { describe, expect, it, vi } from 'vitest'

import { GatewayNonRetryableError, GatewayUnavailableError, HttpGatewayClient } from '../src/gateway-client.js'
import { createGatewayEngineActivities } from '../src/activities/gateway-engine-activities.js'

const project: ProjectExecutionSnapshot = {
  projectId: 'project-1',
  repositoryUri: 'https://github.com/acme/project.git',
  baseBranch: 'main',
  credentialRef: null,
  requestedSourceCommit: null,
}

const localActivities = {} as EngineActivities
const submission: ExecutionSubmission = {
  contractVersion: 'agent-worker/v1', idempotencyKey: 'run-1:QA:1:1', workflowRunId: 'run-1', stage: 'QA', attemptNumber: 1, stageExecutionGeneration: 1, adapterId: 'fake-agent', mode: 'READ', project,
}

describe('HttpGatewayClient', () => {
  it.each([
    [400, 'INVALID_ARGUMENT'],
    [404, 'NOT_FOUND'],
  ])('makes Gateway %i %s responses non-retryable', async (status, code) => {
    const client = new HttpGatewayClient('http://gateway', vi.fn(async () => new Response(JSON.stringify({ code, retryable: false }), { status })))

    await expect(client.submit(submission)).rejects.toBeInstanceOf(GatewayNonRetryableError)
    await expect(client.submit(submission)).rejects.toMatchObject({ code, retryable: false, nonRetryable: true })
  })

  it('keeps Gateway UNAVAILABLE retryable even with an empty error response', async () => {
    const client = new HttpGatewayClient('http://gateway', vi.fn(async () => new Response('', { status: 503 })))

    await expect(client.submit(submission)).rejects.toBeInstanceOf(GatewayUnavailableError)
  })

  it('rejects malformed successful submit, status, and event responses as unavailable', async () => {
    const response = vi.fn(async () => new Response(JSON.stringify({}), { status: 200 }))
    const client = new HttpGatewayClient('http://gateway', response)

    await expect(client.submit(submission)).rejects.toBeInstanceOf(GatewayUnavailableError)
    await expect(client.status('execution-1')).rejects.toBeInstanceOf(GatewayUnavailableError)
    await expect(client.events('execution-1')).rejects.toBeInstanceOf(GatewayUnavailableError)
  })
})

describe('Gateway engine activities', () => {
  it('submits safe intake work to Gateway without workspace or local paths', async () => {
    const submit = vi.fn(async (_submission: ExecutionSubmission) => ({ executionId: 'execution-1' }))
    const gateway = {
      submit,
      status: vi.fn(async () => ({ executionId: 'execution-1', status: 'COMPLETED' as const, terminal: true, artifactRefs: ['intake-1'] })),
      events: vi.fn(async () => []),
    }
    const activities = createGatewayEngineActivities({ gateway, project, localActivities })

    await expect(activities.assessTicket({ metadata: { workflowRunId: 'run-1', stage: 'INTAKE', attemptNumber: 2, version: 1 }, ticketId: 'ticket-1', rawSpecification: 'C:\\private\\spec.md', version: 1 })).resolves.toEqual({
      refinedSpecification: 'intake-1', recommendedChangeType: 'FEATURE', version: 1,
    })

    const submission = submit.mock.calls[0][0]
    expect(submission).toMatchObject({
      contractVersion: 'agent-worker/v1',
      idempotencyKey: 'run-1:INTAKE:2:1',
      workflowRunId: 'run-1', stage: 'INTAKE', attemptNumber: 2, stageExecutionGeneration: 1,
    })
    expect(JSON.stringify(submission)).not.toMatch(/workspaceRef|[A-Za-z]:\\\\|file:/i)
    expect(gateway.events).toHaveBeenCalledWith('execution-1')
  })

  it('propagates retryable Gateway unavailability', async () => {
    const gateway = {
      submit: vi.fn(async (_submission: ExecutionSubmission) => { throw new GatewayUnavailableError() }),
      status: vi.fn(),
      events: vi.fn(),
    }
    const activities = createGatewayEngineActivities({ gateway, project, localActivities })

    await expect(activities.planImplementation({ metadata: { workflowRunId: 'run-1', stage: 'PLANNING', attemptNumber: 1, version: 1 }, refinedSpecification: 'safe', version: 1 })).rejects.toMatchObject({ code: 'UNAVAILABLE', retryable: true })
  })

  it('uses a new generation for a new execution of the same stage', async () => {
    const submit = vi.fn(async (_submission: ExecutionSubmission) => ({ executionId: 'execution-1' }))
    const gateway = { submit, status: vi.fn(async () => ({ executionId: 'execution-1', status: 'COMPLETED' as const, terminal: true, artifactRefs: [] })), events: vi.fn(async () => []) }
    const activities = createGatewayEngineActivities({ gateway, project, localActivities })
    const request = { metadata: { workflowRunId: 'run-1', stage: 'PLANNING' as const, attemptNumber: 1, version: 1 }, refinedSpecification: 'safe', version: 1 }

    await activities.planImplementation(request)
    await activities.planImplementation(request)

    expect(submit.mock.calls.map(([submission]) => submission.stageExecutionGeneration)).toEqual([1, 2])
    expect(submit.mock.calls.map(([submission]) => submission.idempotencyKey)).toEqual(['run-1:PLANNING:1:1', 'run-1:PLANNING:1:2'])
  })

  it('reuses the generation when Temporal retries the same activity execution', async () => {
    const submit = vi.fn(async (_submission: ExecutionSubmission) => ({ executionId: 'execution-1' }))
    const gateway = { submit, status: vi.fn(async () => ({ executionId: 'execution-1', status: 'COMPLETED' as const, terminal: true, artifactRefs: [] })), events: vi.fn(async () => []) }
    const activities = createGatewayEngineActivities({ gateway, project, localActivities, activityId: () => 'temporal-activity-1' })
    const request = { metadata: { workflowRunId: 'run-1', stage: 'PLANNING' as const, attemptNumber: 1, version: 1 }, refinedSpecification: 'safe', version: 1 }

    await activities.planImplementation(request)
    await activities.planImplementation(request)

    expect(submit.mock.calls.map(([submission]) => submission.idempotencyKey)).toEqual(['run-1:PLANNING:1:1', 'run-1:PLANNING:1:1'])
  })

  it('delegates implementation and QA without forwarding their workspace references', async () => {
    const submit = vi.fn(async (submission: ExecutionSubmission) => ({ executionId: `${submission.stage}-execution` }))
    const gateway = {
      submit,
      status: vi.fn(async (executionId: string) => ({ executionId, status: 'COMPLETED' as const, terminal: true, artifactRefs: [] })),
      events: vi.fn(async () => []),
    }
    const implement = vi.fn()
    const runQualityAssurance = vi.fn()
    const activities = createGatewayEngineActivities({ gateway, project, localActivities: { ...localActivities, implement, runQualityAssurance } })
    const metadata = { workflowRunId: 'run-1', stage: 'IMPLEMENTATION' as const, attemptNumber: 1, version: 1 }

    await expect(activities.implement({ metadata, workspaceRef: { value: 'C:\\private\\workspace', version: 1 }, implementationPlanRef: { value: 'plan-1', kind: 'PLAN', version: 1 }, version: 1 })).resolves.toEqual({
      implementationArtifactRef: { value: 'IMPLEMENTATION-execution', kind: 'IMPLEMENTATION', version: 1 }, version: 1,
    })
    await expect(activities.runQualityAssurance({ metadata: { ...metadata, stage: 'QA' }, workspaceRef: { value: 'C:\\private\\workspace', version: 1 }, implementationArtifactRef: { value: 'C:\\private\\artifact', kind: 'IMPLEMENTATION', version: 1 }, version: 1 })).resolves.toEqual({
      passed: true, score: 100, reportRef: { value: 'QA-execution', kind: 'QA_REPORT', version: 1 }, version: 1,
    })

    expect(implement).not.toHaveBeenCalled()
    expect(runQualityAssurance).not.toHaveBeenCalled()
    expect(submit.mock.calls.map(([submission]) => submission.stage)).toEqual(['IMPLEMENTATION', 'QA'])
    expect(JSON.stringify(submit.mock.calls)).not.toMatch(/workspaceRef|[A-Za-z]:\\\\|file:/i)
  })
})
