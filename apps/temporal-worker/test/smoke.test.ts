import { describe, expect, it } from 'vitest'
import { describeWorker } from '../src/worker-info.js'

describe('temporal-worker smoke', () => {
  it('reports its task queue and confirms contracts are reachable across the workspace', () => {
    const info = describeWorker()

    expect(info.taskQueue).toBe('agent-worker-engine')
    expect(info.engineNotificationTopic).toBe('engine-notification-requested')
  })
})
