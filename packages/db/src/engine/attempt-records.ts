import { integer, text, timestamp, unique, uuid } from 'drizzle-orm/pg-core'
import { engineSchema } from './schema.js'
import { workflowRuns } from './workflow-runs.js'

/** One development/QA attempt within a workflow run. FK stays within engine. */
export const attemptRecords = engineSchema.table('attempt_records', {
  id: uuid('id').primaryKey().defaultRandom(),
  workflowRunId: uuid('workflow_run_id').notNull().references(() => workflowRuns.id),
  attemptNumber: integer('attempt_number').notNull(),
  implementationArtifactRef: text('implementation_artifact_ref'),
  qaReportRef: text('qa_report_ref'),
  qaScore: integer('qa_score'),
  status: text('status').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  finishedAt: timestamp('finished_at', { withTimezone: true }),
}, (table) => [
  unique('attempt_records_workflow_run_id_attempt_number_unique')
    .on(table.workflowRunId, table.attemptNumber),
])
