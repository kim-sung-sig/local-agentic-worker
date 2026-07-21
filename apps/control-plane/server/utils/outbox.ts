import type { NodePgDatabase } from 'drizzle-orm/node-postgres'
import { controlPlane } from '@agentic-worker/db'

export interface OutboxEvent {
  aggregateType: string
  aggregateId: string
  eventType: string
  payload: unknown
}

export async function withOutbox<T>(
  db: NodePgDatabase<typeof controlPlane>,
  event: OutboxEvent,
  work: (tx: NodePgDatabase<typeof controlPlane>) => Promise<T>,
): Promise<T> {
  return db.transaction(async (tx) => {
    const result = await work(tx)
    await tx.insert(controlPlane.outboxEvents).values({
      aggregateType: event.aggregateType,
      aggregateId: event.aggregateId,
      eventType: event.eventType,
      payload: event.payload,
    })
    return result
  })
}
