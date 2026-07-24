import { describe, expect, it } from 'vitest'

import { localEngineActivities } from '../src/activities/local-engine-activities.js'

const metadata = { workflowRunId: 'run-1', stage: 'QA' as const, attemptNumber: 1, version: 1 }

describe('localEngineActivities', () => {
  it('registers all eight EngineActivities methods', () => {
    expect(Object.keys(localEngineActivities).sort()).toEqual(
      [
        'assessTicket',
        'implement',
        'manageSourceControl',
        'planImplementation',
        'prepareWorkspace',
        'recordAttemptHistory',
        'runQualityAssurance',
        'sendNotification',
      ].sort(),
    )
  })

  it('delivers notifications as a no-op', async () => {
    await expect(
      localEngineActivities.sendNotification({
        metadata,
        ticketId: 'ticket-1',
        type: 'ACTIVITY_COMPLETED',
        severity: 'INFO',
        title: 't',
        message: 'm',
        version: 1,
      }),
    ).resolves.toEqual({ delivered: true, version: 1 })
  })

  it('records attempt history as a no-op', async () => {
    await expect(
      localEngineActivities.recordAttemptHistory({
        metadata,
        implementationArtifactRef: { value: 'a', kind: 'IMPLEMENTATION', version: 1 },
        qaReportRef: { value: 'r', kind: 'QA_REPORT', version: 1 },
        qaScore: 100,
        status: 'PASSED',
        version: 1,
      }),
    ).resolves.toEqual({ recorded: true, version: 1 })
  })

  it('rejects deferred workspace and source-control stages', async () => {
    await expect(
      localEngineActivities.prepareWorkspace({ metadata, changeType: 'FEATURE', featureSlug: 'f', version: 1 }),
    ).rejects.toThrow(/deferred/i)
    await expect(
      localEngineActivities.manageSourceControl({ metadata, workspaceRef: { value: 'w', version: 1 }, action: 'MERGE', version: 1 }),
    ).rejects.toThrow(/deferred/i)
  })
})
