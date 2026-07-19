export const workflowStages = [
  'INTAKE', 'PLANNING', 'WORKSPACE', 'IMPLEMENTATION', 'QA', 'REVIEW_MERGE',
]

export const mockWorkflowRuns = [
  {
    workflowRunId: '4a44730a-7c7e-4c55-a77d-f520a364734a', ticketId: 'TKT-2481', currentStage: 'QA', status: 'PAUSED',
    attempts: [{ attemptNumber: 1, qaScore: 86, status: 'PASSED', createdAt: '2026-07-17T09:10:00+09:00', finishedAt: '2026-07-17T09:24:00+09:00' }],
  },
  {
    workflowRunId: 'e07a4c8e-7c74-41e0-94fe-3ea5f767a4f6', ticketId: 'TKT-2479', currentStage: 'IMPLEMENTATION', status: 'RUNNING',
    attempts: [
      { attemptNumber: 1, qaScore: 72, status: 'FAILED', createdAt: '2026-07-17T08:30:00+09:00', finishedAt: '2026-07-17T08:46:00+09:00' },
      { attemptNumber: 2, qaScore: null, status: 'RUNNING', createdAt: '2026-07-17T08:50:00+09:00', finishedAt: null },
    ],
  },
  {
    workflowRunId: '6e7c0862-b8b0-4c4e-af75-8812a56e4c3a', ticketId: 'TKT-2473', currentStage: 'REVIEW_MERGE', status: 'RUNNING',
    attempts: [{ attemptNumber: 1, qaScore: 94, status: 'PASSED', createdAt: '2026-07-17T07:15:00+09:00', finishedAt: '2026-07-17T07:32:00+09:00' }],
  },
  {
    workflowRunId: '543c8f7a-f39e-4e7c-8e47-15f07600ff28', ticketId: 'TKT-2469', currentStage: 'QA', status: 'FAILED',
    attempts: [
      { attemptNumber: 1, qaScore: 48, status: 'FAILED', createdAt: '2026-07-16T18:10:00+09:00', finishedAt: '2026-07-16T18:21:00+09:00' },
      { attemptNumber: 2, qaScore: 63, status: 'FAILED', createdAt: '2026-07-16T18:25:00+09:00', finishedAt: '2026-07-16T18:39:00+09:00' },
    ],
  },
]

export function filterRuns(runs, query, status) {
  const text = query.trim().toLowerCase()
  return runs.filter((run) => (!text || `${run.ticketId} ${run.workflowRunId}`.toLowerCase().includes(text))
    && (!status || run.status === status))
}

export function retryRun(run) {
  return { ...run, status: 'RUNNING' }
}

export function applyDecision(run, decision, targetStage = null) {
  if (decision === 'RETRY') return retryRun(run)
  if (decision === 'CANCEL') return { ...run, status: 'CANCELLED' }
  if (decision === 'REQUEST_REVISION') return { ...run, status: 'PAUSED' }
  if (decision === 'REJECT') return { ...run, status: 'PAUSED', rejectionTarget: targetStage }
  if (decision === 'APPROVE' && run.currentStage === 'REVIEW_MERGE') return { ...run, status: 'COMPLETED' }
  return { ...run, status: 'RUNNING' }
}
