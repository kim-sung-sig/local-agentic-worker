import type { EngineActivities, ProjectExecutionSnapshot } from '@agentic-worker/contracts'

import type { GatewayClient } from '../gateway-client.js'

export interface GatewayEngineActivitiesOptions {
  gateway: GatewayClient
  project: ProjectExecutionSnapshot
  localActivities: EngineActivities
  adapterId?: string
}

export function createGatewayEngineActivities({ gateway, project, localActivities, adapterId = 'fake-agent' }: GatewayEngineActivitiesOptions): EngineActivities {
  return {
    ...localActivities,
    assessTicket: async ({ metadata, version }) => {
      const status = await execute(gateway, project, adapterId, metadata, 'INTAKE', 'READ')
      return { refinedSpecification: status.artifactRefs[0] ?? status.executionId, recommendedChangeType: 'FEATURE', version }
    },
    planImplementation: async ({ metadata, version }) => {
      const status = await execute(gateway, project, adapterId, metadata, 'PLANNING', 'READ')
      return {
        implementationPlanRef: { value: status.artifactRefs[0] ?? status.executionId, kind: 'PLAN', version },
        attemptPolicy: { minimumQaScore: 80, maxAttempts: 3, version },
        version,
      }
    },
  }
}

async function execute(
  gateway: GatewayClient,
  project: ProjectExecutionSnapshot,
  adapterId: string,
  metadata: { workflowRunId: string; attemptNumber: number; version: number },
  stage: 'INTAKE' | 'PLANNING',
  mode: 'READ' | 'WRITE',
) {
  const stageExecutionGeneration = metadata.version
  const submission = {
    contractVersion: 'agent-worker/v1' as const,
    idempotencyKey: `${metadata.workflowRunId}:${stage}:${metadata.attemptNumber}:${stageExecutionGeneration}`,
    workflowRunId: metadata.workflowRunId,
    stage,
    attemptNumber: metadata.attemptNumber,
    stageExecutionGeneration,
    adapterId,
    project,
    mode,
  }
  const { executionId } = await gateway.submit(submission)
  const status = await gateway.status(executionId)
  await gateway.events(executionId)
  if (!status.terminal || status.status !== 'COMPLETED') throw new Error(`Gateway execution ${executionId} is ${status.status}`)
  return status
}
