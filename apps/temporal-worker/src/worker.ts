import { fileURLToPath } from 'node:url'

import type { EngineActivities } from '@agentic-worker/contracts'
import { Worker, type WorkerOptions } from '@temporalio/worker'

import { TASK_QUEUE } from './worker-info.js'
import { createGatewayEngineActivities, type GatewayEngineActivitiesOptions } from './activities/gateway-engine-activities.js'

export type CreateAgentWorkerOptions = Omit<WorkerOptions, 'activities' | 'taskQueue' | 'workflowsPath'> & {
  activities: EngineActivities
}

const workflowsPath = fileURLToPath(new URL('./workflows/agent-worker-workflow.ts', import.meta.url))

export function createAgentWorker({ activities, ...options }: CreateAgentWorkerOptions): Promise<Worker> {
  return Worker.create({ ...options, activities, taskQueue: TASK_QUEUE, workflowsPath })
}

export function createGatewayAgentWorker({ localActivities, ...options }: GatewayEngineActivitiesOptions & Omit<CreateAgentWorkerOptions, 'activities'>): Promise<Worker> {
  return createAgentWorker({ ...options, activities: createGatewayEngineActivities({ ...options, localActivities }) })
}
