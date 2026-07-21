import { z } from 'zod'

/** Matches java.util.UUID.fromString() acceptance - no RFC version/variant nibble enforcement. */
const looseUuid = z.string().regex(
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/,
  { message: 'must be a UUID' },
)

/**
 * Published by Agent Engine when a workflow run wants to notify a project's operators.
 * Control Plane consumes this and resolves `ticketId` to a `projectId` itself - Agent Engine
 * never looks up Issue/Notification state directly. TypeScript mirror of the Java record
 * `com.example.worker.contracts.agentworker.EngineNotificationRequested`.
 */
export const EngineNotificationRequestedSchema = z.object({
  workflowRunId: looseUuid,
  ticketId: looseUuid,
  type: z.string().min(1),
  severity: z.string().min(1),
  title: z.string().min(1),
  message: z.string().min(1),
  idempotencyKey: z.string().min(1),
  occurredAt: z.string().datetime(),
})

export type EngineNotificationRequested = z.infer<typeof EngineNotificationRequestedSchema>

export const ENGINE_NOTIFICATION_REQUESTED_TOPIC = 'engine-notification-requested'
