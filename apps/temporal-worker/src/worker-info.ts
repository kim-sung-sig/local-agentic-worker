import { ENGINE_NOTIFICATION_REQUESTED_TOPIC } from '@agentic-worker/contracts'

/** Uses a separate queue while the TypeScript engine runs alongside the Java engine. */
export const TASK_QUEUE = 'agent-worker-engine-typescript'

export function describeWorker() {
  return {
    taskQueue: TASK_QUEUE,
    engineNotificationTopic: ENGINE_NOTIFICATION_REQUESTED_TOPIC,
  }
}
