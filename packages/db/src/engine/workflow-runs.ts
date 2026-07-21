import { text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { engineSchema } from './schema.js'

/**
 * A durable Temporal workflow run projection. ticket_id is a plain uuid (no FK)
 * because it refers to control_plane.issues, which engine may never join to.
 */
export const workflowRuns = engineSchema.table('workflow_runs', {
  id: uuid('id').primaryKey().defaultRandom(),
  ticketId: uuid('ticket_id').notNull(),
  temporalWorkflowId: text('temporal_workflow_id').notNull(),
  currentStage: text('current_stage'),
  status: text('status').notNull().default('RUNNING'),
  workspaceRef: text('workspace_ref'),
  startedAt: timestamp('started_at', { withTimezone: true }).notNull().defaultNow(),
  finishedAt: timestamp('finished_at', { withTimezone: true }),
}, (table) => [
  unique('workflow_runs_temporal_workflow_id_unique').on(table.temporalWorkflowId),
])
