import { fileURLToPath } from 'node:url'

import type { EngineActivities } from '@agentic-worker/contracts'
import { Worker, type WorkerOptions } from '@temporalio/worker'

import { TASK_QUEUE } from './worker-info.js'

export type CreateAgentWorkerOptions = Omit<WorkerOptions, 'activities' | 'taskQueue' | 'workflowsPath'> & {
  activities: EngineActivities
}

const workflowsPath = fileURLToPath(new URL('./workflows/agent-worker-workflow.ts', import.meta.url))

export function createAgentWorker({ activities, ...options }: CreateAgentWorkerOptions): Promise<Worker> {
  return Worker.create({ ...options, activities, taskQueue: TASK_QUEUE, workflowsPath })
}
