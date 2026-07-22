export const WORKFLOW_STAGES = ['INTAKE', 'PLANNING', 'WORKSPACE', 'IMPLEMENTATION', 'QA', 'REVIEW_MERGE'] as const
export type WorkflowStage = (typeof WORKFLOW_STAGES)[number]

export const WORKFLOW_RUN_STATUSES = ['RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED'] as const
export type WorkflowRunStatus = (typeof WORKFLOW_RUN_STATUSES)[number]

export interface StartAgentWorkflowRequest {
  workflowRunId: string
  ticketId: string
  rawSpecification: string
}

export interface ActivityRequestMetadata {
  workflowRunId: string
  stage: WorkflowStage
  attemptNumber: number
  version: number
}

export interface AttemptPolicy {
  minimumQaScore: number
  maxAttempts: number
  version: number
}

export interface ArtifactRef {
  value: string
  kind: string
  version: number
}

export interface WorkspaceRef {
  value: string
  version: number
}

export type AgentWorkflowCommand =
  | { type: 'approve' }
  | { type: 'reject'; reason: string; targetStage: WorkflowStage }
  | { type: 'requestRevision'; reason: string }
  | { type: 'retryStage' }
  | { type: 'cancel' }

export interface EngineActivities {
  assessTicket(request: {
    metadata: ActivityRequestMetadata
    ticketId: string
    rawSpecification: string
    version: number
  }): Promise<{ refinedSpecification: string; recommendedChangeType: string; version: number }>
  planImplementation(request: {
    metadata: ActivityRequestMetadata
    refinedSpecification: string
    version: number
  }): Promise<{ implementationPlanRef: ArtifactRef; attemptPolicy: AttemptPolicy; version: number }>
  prepareWorkspace(request: {
    metadata: ActivityRequestMetadata
    changeType: string
    featureSlug: string
    version: number
  }): Promise<{ workspaceRef: WorkspaceRef; branchName: string; version: number }>
  implement(request: {
    metadata: ActivityRequestMetadata
    workspaceRef: WorkspaceRef
    implementationPlanRef: ArtifactRef
    version: number
  }): Promise<{ implementationArtifactRef: ArtifactRef; version: number }>
  runQualityAssurance(request: {
    metadata: ActivityRequestMetadata
    workspaceRef: WorkspaceRef
    implementationArtifactRef: ArtifactRef
    version: number
  }): Promise<{ passed: boolean; score: number; reportRef: ArtifactRef; version: number }>
  recordAttemptHistory(request: {
    metadata: ActivityRequestMetadata
    implementationArtifactRef: ArtifactRef
    qaReportRef: ArtifactRef
    qaScore: number | null
    status: string
    version: number
  }): Promise<{ recorded: boolean; version: number }>
  manageSourceControl(request: {
    metadata: ActivityRequestMetadata
    workspaceRef: WorkspaceRef
    action: string
    version: number
  }): Promise<{ prUrl: string; status: string; version: number }>
  sendNotification(request: {
    metadata: ActivityRequestMetadata
    ticketId: string
    type: string
    severity: string
    title: string
    message: string
    version: number
  }): Promise<{ delivered: boolean; version: number }>
}
