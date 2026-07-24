import { describe, expect, it, vi } from 'vitest'

import { driveIntakePlanning, type WorkflowDriverClient } from '../src/dev/drive-intake-planning.js'

function fakeClient(stages: Array<'INTAKE' | 'PLANNING'>): { client: WorkflowDriverClient; start: ReturnType<typeof vi.fn>; approve: ReturnType<typeof vi.fn> } {
  const queue = [...stages]
  const start = vi.fn(async () => undefined)
  const approve = vi.fn(async () => undefined)
  const getState = vi.fn(async () => ({ currentStage: queue.shift() ?? 'PLANNING', status: 'RUNNING' as const }))
  return { client: { start, approve, getState }, start, approve }
}

describe('driveIntakePlanning', () => {
  it('starts with a path-like sentinel spec, approves once, and returns at the PLANNING gate', async () => {
    const { client, start, approve } = fakeClient(['INTAKE', 'PLANNING'])

    const state = await driveIntakePlanning(client, 'run-1', { delayMs: 0, sleep: async () => {} })

    expect(state).toEqual({ currentStage: 'PLANNING', status: 'RUNNING' })
    expect(approve).toHaveBeenCalledTimes(1)
    expect(approve).toHaveBeenCalledWith('run-1')
    expect(start).toHaveBeenCalledWith({
      workflowRunId: 'run-1',
      ticketId: 'ticket-run-1',
      rawSpecification: 'C:\\private\\leak-sentinel-run-1.md',
    })
  })

  it('throws if the PLANNING gate is never reached', async () => {
    const start = vi.fn(async () => undefined)
    const approve = vi.fn(async () => undefined)
    const getState = vi.fn(async () => ({ currentStage: 'INTAKE' as const, status: 'RUNNING' as const }))
    const client: WorkflowDriverClient = { start, approve, getState }

    await expect(driveIntakePlanning(client, 'run-2', { attempts: 3, delayMs: 0, sleep: async () => {} })).rejects.toThrow(/PLANNING gate/i)
  })
})
