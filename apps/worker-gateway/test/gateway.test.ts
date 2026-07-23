import { describe, expect, it, vi } from 'vitest'
import type { ExecutionSubmission, WorkerCapabilities } from '@agentic-worker/contracts'
import { GatewayError, WorkerGateway } from '../src/gateway.js'
import { PythonSessionRegistry, type RegisteredPythonSession } from '../src/registry.js'

const submission = (workflowRunId: string): ExecutionSubmission => ({
  contractVersion: 'agent-worker/v1', idempotencyKey: `${workflowRunId}:QA:1:1`, workflowRunId, stage: 'QA', attemptNumber: 1, stageExecutionGeneration: 1, adapterId: 'fake-agent', mode: 'READ',
  project: { projectId: 'project', repositoryUri: 'https://github.com/acme/project.git', baseBranch: 'main', credentialRef: null, requestedSourceCommit: null },
})

function session(sessionId: string, healthy = () => true): RegisteredPythonSession {
  return {
    sessionId, healthy,
    client: {
      submit: vi.fn(async () => ({ executionId: `${sessionId}-execution` })),
      status: vi.fn(async (executionId: string) => ({ executionId, status: 'COMPLETED' as const, terminal: true, artifactRefs: [] })),
      events: vi.fn(async () => []), cancel: vi.fn(async (executionId: string) => ({ executionId, status: 'CANCELLED' as const, terminal: true, artifactRefs: [] })),
      capabilities: vi.fn(async (): Promise<WorkerCapabilities> => ({ workerId: sessionId, adapterIds: ['fake-agent'], modes: ['READ'] })),
    },
  }
}

describe('WorkerGateway', () => {
  it('binds the first submission and reuses that session for the run', async () => {
    const registry = new PythonSessionRegistry(); const first = session('first'); const second = session('second'); registry.register(first); registry.register(second)
    const gateway = new WorkerGateway(registry)
    await gateway.submit(submission('run-1')); await gateway.submit({ ...submission('run-1'), stageExecutionGeneration: 2, idempotencyKey: 'run-1:QA:1:2' })
    expect(first.client.submit).toHaveBeenCalledTimes(2); expect(second.client.submit).not.toHaveBeenCalled()
  })

  it('can choose a different healthy session for a separate run', async () => {
    const registry = new PythonSessionRegistry(); const first = session('first'); const second = session('second'); registry.register(first); registry.register(second)
    const gateway = new WorkerGateway(registry)
    await gateway.submit(submission('run-1')); await gateway.submit(submission('run-2'))
    expect(first.client.submit).toHaveBeenCalledTimes(1); expect(second.client.submit).toHaveBeenCalledTimes(1)
  })

  it('retains a binding when its assigned session becomes unhealthy', async () => {
    let firstHealthy = true; const registry = new PythonSessionRegistry(); const first = session('first', () => firstHealthy); const second = session('second'); registry.register(first); registry.register(second)
    const gateway = new WorkerGateway(registry); const accepted = await gateway.submit(submission('run-1')); firstHealthy = false
    await expect(gateway.status(accepted.executionId)).rejects.toMatchObject({ code: 'UNAVAILABLE', retryable: true })
    await expect(gateway.submit({ ...submission('run-1'), stageExecutionGeneration: 2, idempotencyKey: 'run-1:QA:1:2' })).rejects.toMatchObject({ code: 'UNAVAILABLE', retryable: true })
    expect(second.client.submit).not.toHaveBeenCalled()
  })

  it('returns UNAVAILABLE without reassignment when an assigned client is unreachable', async () => {
    const registry = new PythonSessionRegistry(); const first = session('first'); const second = session('second'); registry.register(first); registry.register(second)
    vi.mocked(first.client.submit).mockRejectedValueOnce(new Error('offline'))
    const gateway = new WorkerGateway(registry)
    await expect(gateway.submit(submission('run-1'))).rejects.toMatchObject({ code: 'UNAVAILABLE', retryable: true })
    await expect(gateway.submit(submission('run-1'))).resolves.toEqual({ executionId: 'first-execution' })
    expect(second.client.submit).not.toHaveBeenCalled()
  })

  it('rejects invalid submissions before choosing a session', async () => {
    const registry = new PythonSessionRegistry(); const first = session('first'); registry.register(first)
    await expect(new WorkerGateway(registry).submit({ ...submission('run-1'), workspaceRef: 'C:\\secret' })).rejects.toEqual(new GatewayError('INVALID_ARGUMENT', false))
    expect(first.client.submit).not.toHaveBeenCalled()
  })
})
