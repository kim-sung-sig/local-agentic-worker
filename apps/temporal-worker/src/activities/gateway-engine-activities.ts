import type { EngineActivities, ProjectExecutionSnapshot } from '@agentic-worker/contracts'
import { Context } from '@temporalio/activity'

import type { GatewayClient } from '../gateway-client.js'

export interface GatewayEngineActivitiesOptions {
  gateway: GatewayClient
  project: ProjectExecutionSnapshot
  localActivities: EngineActivities
  adapterId?: string
  activityId?: () => string | undefined
}

export function createGatewayEngineActivities({ gateway, project, localActivities, adapterId = 'fake-agent', activityId = temporalActivityId }: GatewayEngineActivitiesOptions): EngineActivities {
  const generations = new StageExecutionGenerations(activityId)
  return {
    ...localActivities,
    assessTicket: async ({ metadata, version }) => {
      const status = await execute(gateway, project, adapterId, generations, metadata, 'INTAKE', 'READ')
      return { refinedSpecification: status.artifactRefs[0] ?? status.executionId, recommendedChangeType: 'FEATURE', version }
    },
    planImplementation: async ({ metadata, version }) => {
      const status = await execute(gateway, project, adapterId, generations, metadata, 'PLANNING', 'READ')
      return {
        implementationPlanRef: { value: status.artifactRefs[0] ?? status.executionId, kind: 'PLAN', version },
        attemptPolicy: { minimumQaScore: 80, maxAttempts: 3, version },
        version,
      }
    },
    implement: async ({ metadata, version }) => {
      const status = await execute(gateway, project, adapterId, generations, metadata, 'IMPLEMENTATION', 'WRITE')
      return { implementationArtifactRef: { value: status.artifactRefs[0] ?? status.executionId, kind: 'IMPLEMENTATION', version }, version }
    },
    runQualityAssurance: async ({ metadata, version }) => {
      const status = await execute(gateway, project, adapterId, generations, metadata, 'QA', 'READ')
      return { passed: true, score: 100, reportRef: { value: status.artifactRefs[0] ?? status.executionId, kind: 'QA_REPORT', version }, version }
    },
  }
}

async function execute(
  gateway: GatewayClient,
  project: ProjectExecutionSnapshot,
  adapterId: string,
  generations: StageExecutionGenerations,
  metadata: { workflowRunId: string; attemptNumber: number; version: number },
  stage: 'INTAKE' | 'PLANNING' | 'IMPLEMENTATION' | 'QA',
  mode: 'READ' | 'WRITE',
) {
  const stageExecutionGeneration = generations.next(metadata.workflowRunId, stage)
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

class StageExecutionGenerations {
  private readonly values = new Map<string, number>()
  private readonly byActivityId = new Map<string, number>()

  constructor(private readonly activityId: () => string | undefined) {}

  next(workflowRunId: string, stage: string): number {
    const stageKey = `${workflowRunId}:${stage}`
    const activityId = this.activityId()
    const numericActivityId = activityId && /^[1-9]\d*$/.test(activityId) ? Number(activityId) : undefined
    if (numericActivityId !== undefined) return numericActivityId
    const executionKey = activityId ? `${stageKey}:${activityId}` : undefined
    if (executionKey && this.byActivityId.has(executionKey)) return this.byActivityId.get(executionKey)!
    const generation = (this.values.get(stageKey) ?? 0) + 1
    this.values.set(stageKey, generation)
    if (executionKey) this.byActivityId.set(executionKey, generation)
    return generation
  }
}

function temporalActivityId(): string | undefined {
  try {
    return Context.current().info.activityId
  } catch {
    return undefined
  }
}
