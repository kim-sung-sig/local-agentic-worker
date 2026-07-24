import type { StartAgentWorkflowRequest, WorkflowRunStatus, WorkflowStage } from '@agentic-worker/contracts'

export interface WorkflowDriverClient {
  start(request: StartAgentWorkflowRequest): Promise<unknown>
  approve(workflowId: string): Promise<unknown>
  getState(workflowId: string): Promise<{ currentStage: WorkflowStage | undefined; status: WorkflowRunStatus }>
}

export interface DriveOptions {
  attempts?: number
  delayMs?: number
  sleep?: (ms: number) => Promise<void>
}

export async function driveIntakePlanning(
  client: WorkflowDriverClient,
  runId: string,
  options: DriveOptions = {},
): Promise<{ currentStage: WorkflowStage | undefined; status: WorkflowRunStatus }> {
  const attempts = options.attempts ?? 30
  const delayMs = options.delayMs ?? 1000
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)))

  // Path-like sentinel proves INTAKE input never reaches the Gateway/ledger.
  const rawSpecification = `C:\\private\\leak-sentinel-${runId}.md`
  await client.start({ workflowRunId: runId, ticketId: `ticket-${runId}`, rawSpecification })

  // One approval clears the INTAKE gate; PLANNING then runs and parks at its gate.
  await client.approve(runId)

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const state = await client.getState(runId)
    if (state.currentStage === 'PLANNING' && state.status === 'RUNNING') return state
    await sleep(delayMs)
  }
  throw new Error(`workflow ${runId} did not reach the PLANNING gate`)
}
