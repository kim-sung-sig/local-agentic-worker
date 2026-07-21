import { index, text, timestamp, uuid } from 'drizzle-orm/pg-core'
import { engineSchema } from './schema.js'
import { workflowRuns } from './workflow-runs.js'

/** A stage gate decision recorded for a workflow run. FK stays within engine. */
export const stageGates = engineSchema.table('stage_gates', {
  id: uuid('id').primaryKey().defaultRandom(),
  workflowRunId: uuid('workflow_run_id').notNull().references(() => workflowRuns.id),
  stage: text('stage').notNull(),
  decision: text('decision').notNull(),
  reason: text('reason'),
  decidedAt: timestamp('decided_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  index('stage_gates_workflow_run_id_idx').on(table.workflowRunId),
])
