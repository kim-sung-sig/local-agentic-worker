import { Client } from '@temporalio/client'
import type { StartAgentWorkflowRequest, WorkflowRunStatus, WorkflowStage } from '@agentic-worker/contracts'

import { TASK_QUEUE } from './worker-info.js'
import {
  approve,
  cancel,
  currentStage,
  reject,
  requestRevision,
  retryStage,
  run,
  status,
} from './workflows/agent-worker-workflow.js'

export class AgentWorkflowClient {
  constructor(private readonly client: Pick<Client, 'workflow'> = new Client()) {}

  start(request: StartAgentWorkflowRequest) {
    return this.client.workflow.start(run, {
      workflowId: request.workflowRunId,
      taskQueue: TASK_QUEUE,
      args: [request],
    })
  }

  approve(workflowId: string) {
    return this.client.workflow.getHandle(workflowId).signal(approve)
  }

  reject(workflowId: string, reason: string, targetStage: WorkflowStage) {
    return this.client.workflow.getHandle(workflowId).signal(reject, reason, targetStage)
  }

  requestRevision(workflowId: string, reason: string) {
    return this.client.workflow.getHandle(workflowId).signal(requestRevision, reason)
  }

  retryStage(workflowId: string) {
    return this.client.workflow.getHandle(workflowId).signal(retryStage)
  }

  cancel(workflowId: string) {
    return this.client.workflow.getHandle(workflowId).signal(cancel)
  }

  async getState(workflowId: string): Promise<{ currentStage: WorkflowStage | undefined; status: WorkflowRunStatus }> {
    const handle = this.client.workflow.getHandle(workflowId)
    const [stage, runStatus] = await Promise.all([handle.query(currentStage), handle.query(status)])
    return { currentStage: stage, status: runStatus }
  }
}
