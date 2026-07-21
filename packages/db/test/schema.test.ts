import { describe, expect, it } from 'vitest'
import { getTableConfig } from 'drizzle-orm/pg-core'
import * as controlPlane from '../src/control-plane/index.js'
import * as engine from '../src/engine/index.js'
import { controlPlaneSchema, engineSchema } from '../src/index.js'

function columnNames(table: Parameters<typeof getTableConfig>[0]): string[] {
  return getTableConfig(table).columns.map((column) => column.name)
}

function hasUniqueOn(table: Parameters<typeof getTableConfig>[0], names: string[]): boolean {
  return getTableConfig(table).uniqueConstraints.some((unique) => {
    const uniqueColumnNames = unique.columns.map((column) => column.name).sort()
    return uniqueColumnNames.length === names.length
      && uniqueColumnNames.join(',') === [...names].sort().join(',')
  })
}

function defaultOf(table: Parameters<typeof getTableConfig>[0], columnName: string): unknown {
  const column = getTableConfig(table).columns.find((candidate) => candidate.name === columnName)
  return column?.default
}

describe('two-schema namespace boundary', () => {
  it('exposes control_plane and engine as separate pgSchema namespaces', () => {
    expect(controlPlaneSchema.schemaName).toBe('control_plane')
    expect(engineSchema.schemaName).toBe('engine')
  })

  it('never defines a foreign key crossing the control_plane/engine boundary', () => {
    const allTables = [
      controlPlane.projects,
      controlPlane.issues,
      controlPlane.documents,
      controlPlane.documentRevisions,
      controlPlane.documentArtifacts,
      controlPlane.notifications,
      controlPlane.users,
      controlPlane.memberships,
      controlPlane.sessions,
      controlPlane.outboxEvents,
      engine.workflowRuns,
      engine.stageGates,
      engine.attemptRecords,
    ]

    for (const table of allTables) {
      const config = getTableConfig(table)
      for (const fk of config.foreignKeys) {
        const reference = fk.reference()
        const sourceSchema = config.schema
        const targetSchema = getTableConfig(reference.foreignTable).schema
        expect(targetSchema).toBe(sourceSchema)
      }
    }
  })
})

describe('control_plane.projects', () => {
  it('has expected columns and base_branch default', () => {
    const names = columnNames(controlPlane.projects)
    expect(names).toEqual(expect.arrayContaining([
      'id', 'name', 'local_path', 'base_branch', 'repository_uri', 'credential_ref', 'created_at',
    ]))
    expect(defaultOf(controlPlane.projects, 'base_branch')).toBe('main')
  })

  it('allows local_path to be nullable', () => {
    const column = getTableConfig(controlPlane.projects).columns.find((c) => c.name === 'local_path')
    expect(column?.notNull).toBe(false)
  })
})

describe('control_plane.issues', () => {
  it('references projects within the same schema and defaults status to OPEN', () => {
    const config = getTableConfig(controlPlane.issues)
    expect(defaultOf(controlPlane.issues, 'status')).toBe('OPEN')
    expect(hasUniqueOn(controlPlane.issues, ['project_id', 'issue_number'])).toBe(true)

    const fk = config.foreignKeys.find((f) => f.reference().foreignTable === controlPlane.projects)
    expect(fk).toBeDefined()
    expect(getTableConfig(fk!.reference().foreignTable).schema).toBe('control_plane')
  })
})

describe('control_plane.documents', () => {
  it('supports the 7 required document kinds', () => {
    const values = controlPlane.documentKindEnum.enumValues
    expect(values).toEqual(expect.arrayContaining([
      'PROMPT_TEMPLATE',
      'DEVELOPMENT_GUIDE',
      'QA_GUIDE',
      'PLAN',
      'IMPLEMENTATION_PLAN',
      'DEVELOPMENT_RESULT',
      'QA_REPORT',
    ]))
    expect(values.length).toBe(7)
  })
})

describe('control_plane.document_revisions', () => {
  it('is append-only: unique per document + revision_number for ordering, no updated_at column', () => {
    expect(hasUniqueOn(controlPlane.documentRevisions, ['document_id', 'revision_number'])).toBe(true)
    const names = columnNames(controlPlane.documentRevisions)
    expect(names).not.toContain('updated_at')
  })
})

describe('control_plane.document_artifacts', () => {
  it('links back to a document revision', () => {
    const names = columnNames(controlPlane.documentArtifacts)
    expect(names).toEqual(expect.arrayContaining(['id', 'document_id']))
  })
})

describe('control_plane.notifications', () => {
  it('has unique notification_id and event_key, plus cursor/unread indexes', () => {
    const config = getTableConfig(controlPlane.notifications)
    expect(hasUniqueOn(controlPlane.notifications, ['notification_id'])).toBe(true)
    expect(hasUniqueOn(controlPlane.notifications, ['event_key'])).toBe(true)

    const names = columnNames(controlPlane.notifications)
    expect(names).toEqual(expect.arrayContaining(['read_at', 'created_at']))

    expect(config.indexes.length).toBeGreaterThanOrEqual(2)
  })
})

describe('control_plane.users / memberships / sessions', () => {
  it('defines the three auth tables', () => {
    expect(columnNames(controlPlane.users)).toEqual(expect.arrayContaining(['id', 'email']))
    expect(columnNames(controlPlane.memberships)).toEqual(expect.arrayContaining(['id', 'user_id', 'project_id']))
    expect(columnNames(controlPlane.sessions)).toEqual(expect.arrayContaining(['id', 'user_id']))
  })
})

describe('control_plane.outbox_events', () => {
  it('defines a transactional outbox row shape', () => {
    expect(columnNames(controlPlane.outboxEvents)).toEqual(expect.arrayContaining([
      'id', 'event_type', 'payload', 'created_at', 'processed_at',
    ]))
  })
})

describe('engine.workflow_runs', () => {
  it('has unique temporal_workflow_id, status default RUNNING, and a non-FK ticket_id column', () => {
    const config = getTableConfig(engine.workflowRuns)
    expect(hasUniqueOn(engine.workflowRuns, ['temporal_workflow_id'])).toBe(true)
    expect(defaultOf(engine.workflowRuns, 'status')).toBe('RUNNING')

    const ticketIdColumn = config.columns.find((c) => c.name === 'ticket_id')
    expect(ticketIdColumn).toBeDefined()
    expect(config.foreignKeys.some((fk) => fk.reference().columns.some((c) => c.name === 'ticket_id'))).toBe(false)
  })
})

describe('engine.stage_gates', () => {
  it('references workflow_runs within the engine schema', () => {
    const config = getTableConfig(engine.stageGates)
    const fk = config.foreignKeys.find((f) => f.reference().foreignTable === engine.workflowRuns)
    expect(fk).toBeDefined()
    expect(getTableConfig(fk!.reference().foreignTable).schema).toBe('engine')
  })
})

describe('engine.attempt_records', () => {
  it('references workflow_runs, has qa_score int, and unique(run,attempt_number)', () => {
    const config = getTableConfig(engine.attemptRecords)
    const fk = config.foreignKeys.find((f) => f.reference().foreignTable === engine.workflowRuns)
    expect(fk).toBeDefined()
    expect(hasUniqueOn(engine.attemptRecords, ['workflow_run_id', 'attempt_number'])).toBe(true)

    const qaScoreColumn = config.columns.find((c) => c.name === 'qa_score')
    expect(qaScoreColumn?.dataType).toBe('number')
  })
})
