import { expect, it } from 'vitest'
import { WORKFLOW_STAGES, WORKFLOW_RUN_STATUSES } from '../src/agent-engine.js'
import type { ActivityRequestMetadata, ArtifactRef, WorkspaceRef } from '../src/agent-engine.js'

it('keeps the Java workflow stage order and terminal statuses', () => {
  expect(WORKFLOW_STAGES).toEqual(['INTAKE', 'PLANNING', 'WORKSPACE', 'IMPLEMENTATION', 'QA', 'REVIEW_MERGE'])
  expect(WORKFLOW_RUN_STATUSES).toContain('COMPLETED')
  expect(WORKFLOW_RUN_STATUSES).toContain('CANCELLED')
})

it('keeps activity metadata and references versioned', () => {
  const artifact: ArtifactRef = { value: 'plan-1', kind: 'PLAN', version: 1 }
  const workspace: WorkspaceRef = { value: 'workspace-1', version: 1 }
  const metadata: ActivityRequestMetadata = {
    workflowRunId: 'run-1',
    stage: 'PLANNING',
    attemptNumber: 1,
    version: artifact.version,
  }

  expect([metadata.version, workspace.version]).toEqual([artifact.version, artifact.version])
})
