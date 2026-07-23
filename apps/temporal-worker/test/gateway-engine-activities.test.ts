import type { EngineActivities, ExecutionSubmission, ProjectExecutionSnapshot } from '@agentic-worker/contracts'
import { describe, expect, it, vi } from 'vitest'

import { GatewayUnavailableError } from '../src/gateway-client.js'
import { createGatewayEngineActivities } from '../src/activities/gateway-engine-activities.js'

const project: ProjectExecutionSnapshot = {
  projectId: 'project-1',
  repositoryUri: 'https://github.com/acme/project.git',
  baseBranch: 'main',
  credentialRef: null,
  requestedSourceCommit: null,
}

const localActivities = {} as EngineActivities

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
})
