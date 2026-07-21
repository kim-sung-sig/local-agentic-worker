import { describe, expect, it } from 'vitest'
import { ENGINE_NOTIFICATION_REQUESTED_TOPIC, EngineNotificationRequestedSchema } from '../src/engine-notification-requested.js'

describe('EngineNotificationRequested', () => {
  it('parses an engine-published notification request', () => {
    const parsed = EngineNotificationRequestedSchema.parse({
      workflowRunId: '22222222-2222-2222-2222-222222222222',
      ticketId: '11111111-1111-1111-1111-111111111111',
      type: 'ACTIVITY_COMPLETED',
      severity: 'INFO',
      title: 'QA 통과',
      message: 'QA 점수 95',
      idempotencyKey: 'wf:QA:1:ACTIVITY_COMPLETED:QA 통과:QA 점수 95',
      occurredAt: '2026-07-16T00:00:00Z',
    })

    expect(parsed.workflowRunId).toBe('22222222-2222-2222-2222-222222222222')
    expect(parsed.ticketId).toBe('11111111-1111-1111-1111-111111111111')
  })

  it('exposes a stable topic name', () => {
    expect(ENGINE_NOTIFICATION_REQUESTED_TOPIC).toBe('engine-notification-requested')
  })
})
