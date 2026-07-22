import { fileURLToPath } from 'node:url'

import { TestWorkflowEnvironment } from '@temporalio/testing'
import { Worker } from '@temporalio/worker'
import type { EngineActivities, StartAgentWorkflowRequest } from '@agentic-worker/contracts'
import { afterAll, beforeAll, describe, expect, test, vi } from 'vitest'

import {
  approve,
  cancel,
  currentStage,
  reject,
  retryStage,
  run,
  status,
} from '../src/workflows/agent-worker-workflow.js'

const workflowsPath = fileURLToPath(new URL('../src/workflows/agent-worker-workflow.ts', import.meta.url))
const request: StartAgentWorkflowRequest = {
  workflowRunId: 'run-1',
  ticketId: 'ticket-1',
  rawSpecification: 'Add a deterministic workflow',
}

let environment: TestWorkflowEnvironment
let sequence = 0

beforeAll(async () => {
  environment = await TestWorkflowEnvironment.createTimeSkipping()
}, 120_000)

afterAll(async () => {
  await environment?.teardown()
}, 120_000)

function fakeActivities(score = 100, maxAttempts = 2, calls: string[] = [], implementationAttempts: number[] = []): EngineActivities {
  return {
    assessTicket: async () => {
      calls.push('INTAKE')
      return { refinedSpecification: 'refined', recommendedChangeType: 'FEATURE', version: 1 }
    },
    planImplementation: async () => {
      calls.push('PLANNING')
      return { implementationPlanRef: { value: 'plan-1', kind: 'PLAN', version: 1 }, attemptPolicy: { minimumQaScore: 80, maxAttempts, version: 1 }, version: 1 }
    },
    prepareWorkspace: async () => {
      calls.push('WORKSPACE')
      return { workspaceRef: { value: 'workspace-1', version: 1 }, branchName: 'feature/run-1', version: 1 }
    },
    implement: async ({ metadata }) => {
      calls.push('IMPLEMENTATION')
      implementationAttempts.push(metadata.attemptNumber)
      return { implementationArtifactRef: { value: 'implementation-1', kind: 'IMPLEMENTATION', version: 1 }, version: 1 }
    },
    runQualityAssurance: async () => {
      calls.push('QA')
      return { passed: score >= 80, score, reportRef: { value: 'qa-1', kind: 'QA_REPORT', version: 1 }, version: 1 }
    },
    recordAttemptHistory: async () => ({ recorded: true, version: 1 }),
    manageSourceControl: async ({ action }) => {
      calls.push(action)
      return { prUrl: 'https://example.test/pr/1', status: action, version: 1 }
    },
    sendNotification: async () => ({ delivered: true, version: 1 }),
  }
}

async function startWorkflow(activities: EngineActivities) {
  const taskQueue = `agent-worker-workflow-test-${++sequence}`
  const worker = await Worker.create({
    connection: environment.nativeConnection,
    taskQueue,
    workflowsPath,
    activities,
  })
  const workerRun = worker.run()
  const handle = await environment.client.workflow.start(run, {
    taskQueue,
    workflowId: `workflow-${sequence}`,
    args: [{ ...request, workflowRunId: `run-${sequence}` }],
  })
  return { handle, worker, workerRun }
}

async function stop(worker: Worker, workerRun: Promise<void>) {
  worker.shutdown()
  await workerRun
}

async function expectState(handle: Awaited<ReturnType<typeof startWorkflow>>['handle'], expectedStage: string, expectedStatus = 'RUNNING') {
  await vi.waitFor(async () => {
    await expect(handle.query(currentStage)).resolves.toBe(expectedStage)
    await expect(handle.query(status)).resolves.toBe(expectedStatus)
  })
}

describe.sequential('agent worker workflow', () => {
  test('waits only at Java approval gates while workspace and implementation advance automatically', async () => {
    const calls: string[] = []
    const { handle, worker, workerRun } = await startWorkflow(fakeActivities(100, 2, calls))

    await expectState(handle, 'INTAKE')
    await handle.signal(approve)
    await expectState(handle, 'PLANNING')
    await handle.signal(approve)
    await expectState(handle, 'QA')
    expect(calls).toEqual(['INTAKE', 'PLANNING', 'WORKSPACE', 'IMPLEMENTATION', 'QA'])
    await handle.signal(approve)
    await expectState(handle, 'REVIEW_MERGE')
    await handle.signal(approve)
    await expect(handle.result()).resolves.toBe('COMPLETED')
    await expect(Worker.runReplayHistory({ workflowsPath }, await handle.fetchHistory(), handle.workflowId)).resolves.toBeUndefined()
    await stop(worker, workerRun)
  }, 30_000)

  test('fails after the final low-score QA attempt', async () => {
    const calls: string[] = []
    const { handle, worker, workerRun } = await startWorkflow(fakeActivities(50, 2, calls))

    await expectState(handle, 'INTAKE')
    await handle.signal(approve)
    await expectState(handle, 'PLANNING')
    await handle.signal(approve)
    await expect(handle.result()).resolves.toBe('FAILED')
    expect(calls.filter((call) => call === 'IMPLEMENTATION')).toHaveLength(2)
    expect(calls.filter((call) => call === 'QA')).toHaveLength(2)
    await stop(worker, workerRun)
  }, 30_000)

  test('pauses on a planning rejection and retries that stage', async () => {
    const { handle, worker, workerRun } = await startWorkflow(fakeActivities())

    await expectState(handle, 'INTAKE')
    await handle.signal(approve)
    await expectState(handle, 'PLANNING')
    await handle.signal(reject, 'needs changes', 'PLANNING')
    await expectState(handle, 'PLANNING', 'PAUSED')
    await handle.signal(retryStage)
    await expectState(handle, 'PLANNING')
    await handle.signal(cancel)
    await expect(handle.result()).resolves.toBe('CANCELLED')
    await stop(worker, workerRun)
  }, 30_000)

  test('increments the attempt before reimplementing after a QA rejection', async () => {
    const implementationAttempts: number[] = []
    const { handle, worker, workerRun } = await startWorkflow(fakeActivities(100, 2, [], implementationAttempts))

    await expectState(handle, 'INTAKE')
    await handle.signal(approve)
    await expectState(handle, 'PLANNING')
    await handle.signal(approve)
    await expectState(handle, 'QA')
    await handle.signal(reject, 'reimplement', 'IMPLEMENTATION')
    await expectState(handle, 'QA', 'PAUSED')
    await handle.signal(retryStage)
    await expectState(handle, 'QA')
    expect(implementationAttempts).toEqual([1, 2])
    await handle.signal(cancel)
    await expect(handle.result()).resolves.toBe('CANCELLED')
    await stop(worker, workerRun)
  }, 30_000)

  test('ignores a forward rejection while intake is awaiting approval', async () => {
    const { handle, worker, workerRun } = await startWorkflow(fakeActivities())

    await expectState(handle, 'INTAKE')
    await handle.signal(reject, 'not yet', 'REVIEW_MERGE')
    await expectState(handle, 'INTAKE')
    await handle.signal(cancel)
    await expect(handle.result()).resolves.toBe('CANCELLED')
    await stop(worker, workerRun)
  }, 30_000)

  test('cancels at an approval gate', async () => {
    const { handle, worker, workerRun } = await startWorkflow(fakeActivities())

    await expectState(handle, 'INTAKE')
    await handle.signal(cancel)
    await expect(handle.result()).resolves.toBe('CANCELLED')
    await stop(worker, workerRun)
  }, 30_000)
})
