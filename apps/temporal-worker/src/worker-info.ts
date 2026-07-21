import { ENGINE_NOTIFICATION_REQUESTED_TOPIC } from '@agentic-worker/contracts'

/** Matches the Java engine's `agent.engine.temporal.task-queue` property during the migration. */
export const TASK_QUEUE = 'agent-worker-engine'

export function describeWorker() {
  return {
    taskQueue: TASK_QUEUE,
    engineNotificationTopic: ENGINE_NOTIFICATION_REQUESTED_TOPIC,
  }
}
