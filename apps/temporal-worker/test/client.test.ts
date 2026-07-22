import type { Client } from '@temporalio/client'
import type { EngineActivities, StartAgentWorkflowRequest } from '@agentic-worker/contracts'
import { describe, expect, it, vi } from 'vitest'

const { createWorker } = vi.hoisted(() => ({ createWorker: vi.fn() }))

vi.mock('@temporalio/worker', () => ({ Worker: { create: createWorker } }))

import { AgentWorkflowClient } from '../src/client.js'
import { createAgentWorker } from '../src/worker.js'
import { TASK_QUEUE } from '../src/worker-info.js'
import {
  approve,
  cancel,
  currentStage,
  reject,
  requestRevision,
  retryStage,
  run,
  status,
} from '../src/workflows/agent-worker-workflow.js'

const request: StartAgentWorkflowRequest = {
  workflowRunId: 'run-1',
  ticketId: 'ticket-1',
  rawSpecification: 'Implement the client wrapper',
}

function fakeClient() {
  const handle = { signal: vi.fn(), query: vi.fn() }
  const workflow = { start: vi.fn(), getHandle: vi.fn(() => handle) }
  return { client: { workflow } as unknown as Client, workflow, handle }
}

describe('AgentWorkflowClient', () => {
  it('starts run on the TypeScript migration queue with the request workflow id', async () => {
    const { client, workflow } = fakeClient()

    await new AgentWorkflowClient(client).start(request)

    expect(workflow.start).toHaveBeenCalledOnce()
    expect(workflow.start).toHaveBeenCalledWith(run, {
      workflowId: request.workflowRunId,
      taskQueue: TASK_QUEUE,
      args: [request],
    })
  })

  it.each([
    ['approve', (client: AgentWorkflowClient) => client.approve('run-1'), approve, []],
    ['reject', (client: AgentWorkflowClient) => client.reject('run-1', 'needs changes', 'PLANNING'), reject, ['needs changes', 'PLANNING']],
    ['requestRevision', (client: AgentWorkflowClient) => client.requestRevision('run-1', 'clarify scope'), requestRevision, ['clarify scope']],
    ['retryStage', (client: AgentWorkflowClient) => client.retryStage('run-1'), retryStage, []],
    ['cancel', (client: AgentWorkflowClient) => client.cancel('run-1'), cancel, []],
  ] as const)('maps %s to one workflow signal', async (_name, command, signal, args) => {
    const { client, workflow, handle } = fakeClient()

    await command(new AgentWorkflowClient(client))

    expect(workflow.getHandle).toHaveBeenCalledOnce()
    expect(workflow.getHandle).toHaveBeenCalledWith('run-1')
    expect(handle.signal).toHaveBeenCalledOnce()
    expect(handle.signal).toHaveBeenCalledWith(signal, ...args)
  })

  it('queries current stage and status once each', async () => {
    const { client, workflow, handle } = fakeClient()
    handle.query.mockResolvedValueOnce('QA').mockResolvedValueOnce('RUNNING')

    await expect(new AgentWorkflowClient(client).getState('run-1')).resolves.toEqual({ currentStage: 'QA', status: 'RUNNING' })

    expect(workflow.getHandle).toHaveBeenCalledOnce()
    expect(handle.query).toHaveBeenCalledTimes(2)
    expect(handle.query).toHaveBeenNthCalledWith(1, currentStage)
    expect(handle.query).toHaveBeenNthCalledWith(2, status)
  })
})

describe('createAgentWorker', () => {
  it('creates but does not run a worker with injected activities on the migration queue', async () => {
    const worker = { run: vi.fn() }
    const activities = {} as EngineActivities
    createWorker.mockResolvedValueOnce(worker)

    await expect(createAgentWorker({ activities, identity: 'test-worker' })).resolves.toBe(worker)

    expect(createWorker).toHaveBeenCalledOnce()
    expect(createWorker).toHaveBeenCalledWith(expect.objectContaining({
      activities,
      identity: 'test-worker',
      taskQueue: TASK_QUEUE,
      workflowsPath: expect.stringMatching(/agent-worker-workflow\.ts$/),
    }))
    expect(worker.run).not.toHaveBeenCalled()
  })
})
