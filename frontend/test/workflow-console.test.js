import test from 'node:test'
import assert from 'node:assert/strict'
import { applyDecision, filterRuns, retryRun } from '../src/lib/workflow-console.js'

test('filters workflow runs by text and status', () => {
  const runs = [{ workflowRunId: 'run-1', ticketId: 'TKT-2481', status: 'PAUSED' }]

  assert.deepEqual(filterRuns(runs, '2481', 'PAUSED'), runs)
})

test('retries a paused workflow run', () => {
  assert.equal(retryRun({ status: 'PAUSED' }).status, 'RUNNING')
})

test('approving review and merge completes the workflow run', () => {
  const result = applyDecision({ currentStage: 'REVIEW_MERGE', status: 'RUNNING' }, 'APPROVE')

  assert.equal(result.status, 'COMPLETED')
})

test('cancelling a workflow run cancels it', () => {
  assert.equal(applyDecision({ status: 'RUNNING' }, 'CANCEL').status, 'CANCELLED')
})

test('rejecting a workflow run keeps the selected target stage', () => {
  const result = applyDecision({ status: 'RUNNING' }, 'REJECT', 'PLANNING')

  assert.equal(result.rejectionTarget, 'PLANNING')
})
