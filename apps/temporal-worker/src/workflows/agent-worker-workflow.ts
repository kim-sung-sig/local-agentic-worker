import type {
  ActivityRequestMetadata,
  ArtifactRef,
  EngineActivities,
  StartAgentWorkflowRequest,
  WorkflowRunStatus,
  WorkflowStage,
  WorkspaceRef,
} from '@agentic-worker/contracts'
import { condition, defineQuery, defineSignal, proxyActivities, setHandler } from '@temporalio/workflow'

const activities = proxyActivities<EngineActivities>({
  startToCloseTimeout: '10 minutes',
  retry: { maximumAttempts: 3 },
})

const stages: WorkflowStage[] = ['INTAKE', 'PLANNING', 'WORKSPACE', 'IMPLEMENTATION', 'QA', 'REVIEW_MERGE']
const CONTRACT_VERSION = 1

export const approve = defineSignal('approve')
export const reject = defineSignal<[reason: string, targetStage: WorkflowStage]>('reject')
export const requestRevision = defineSignal<[reason: string]>('requestRevision')
export const retryStage = defineSignal('retryStage')
export const cancel = defineSignal('cancel')
export const currentStage = defineQuery<WorkflowStage | undefined>('currentStage')
export const status = defineQuery<WorkflowRunStatus>('status')

export async function run(request: StartAgentWorkflowRequest): Promise<WorkflowRunStatus> {
  let stage: WorkflowStage | undefined = 'INTAKE'
  let runStatus: WorkflowRunStatus = 'RUNNING'
  let attemptNumber = 1
  let approved = false
  let cancelled = false
  let retryRequested = false
  let rejectionTarget: WorkflowStage | undefined
  let refinedSpecification = ''
  let changeType = ''
  let implementationPlanRef: ArtifactRef = { value: '', kind: 'PLAN', version: CONTRACT_VERSION }
  let minimumQaScore = 0
  let maxAttempts = 1
  let workspaceRef: WorkspaceRef = { value: '', version: CONTRACT_VERSION }
  let implementationArtifactRef: ArtifactRef = { value: '', kind: 'IMPLEMENTATION', version: CONTRACT_VERSION }

  const metadata = (activityStage: WorkflowStage): ActivityRequestMetadata => ({
    workflowRunId: request.workflowRunId,
    stage: activityStage,
    attemptNumber,
    version: CONTRACT_VERSION,
  })
  const notify = (activityStage: WorkflowStage, type: string, severity: string, title: string, message: string) =>
    activities.sendNotification({ metadata: metadata(activityStage), ticketId: request.ticketId, type, severity, title, message, version: CONTRACT_VERSION })
  const transitionTo = (next: WorkflowStage) => {
    stage = next
  }
  const cancelRun = () => {
    runStatus = 'CANCELLED'
    stage = undefined
  }
  const awaitGate = async (): Promise<boolean> => {
    await condition(() => approved || cancelled || rejectionTarget !== undefined)
    if (cancelled) {
      cancelRun()
      return false
    }
    if (rejectionTarget !== undefined) {
      const target = rejectionTarget
      approved = false
      rejectionTarget = undefined
      runStatus = 'PAUSED'
      await condition(() => retryRequested || cancelled)
      if (cancelled) {
        cancelRun()
        return false
      }
      retryRequested = false
      runStatus = 'RUNNING'
      transitionTo(target)
      return false
    }
    approved = false
    return true
  }

  setHandler(approve, () => {
    approved = true
  })
  setHandler(reject, (reason, targetStage) => {
    if (stage === undefined || stages.indexOf(targetStage) > stages.indexOf(stage)) return
    rejectionTarget = targetStage
    void reason
  })
  setHandler(requestRevision, (reason) => {
    if (stage !== undefined) rejectionTarget = stage
    void reason
  })
  setHandler(retryStage, () => {
    retryRequested = true
  })
  setHandler(cancel, () => {
    cancelled = true
  })
  setHandler(currentStage, () => stage)
  setHandler(status, () => runStatus)

  while (stage !== undefined && runStatus === 'RUNNING') {
    switch (stage as WorkflowStage) {
      case 'INTAKE': {
        const assessment = await activities.assessTicket({ metadata: metadata(stage), ticketId: request.ticketId, rawSpecification: request.rawSpecification, version: CONTRACT_VERSION })
        refinedSpecification = assessment.refinedSpecification
        changeType = assessment.recommendedChangeType
        await notify(stage, 'ACTIVITY_COMPLETED', 'INFO', 'INTAKE complete', 'Ticket assessed')
        if (await awaitGate()) transitionTo('PLANNING')
        break
      }
      case 'PLANNING': {
        const plan = await activities.planImplementation({ metadata: metadata(stage), refinedSpecification, version: CONTRACT_VERSION })
        implementationPlanRef = plan.implementationPlanRef
        minimumQaScore = plan.attemptPolicy.minimumQaScore
        maxAttempts = plan.attemptPolicy.maxAttempts
        await notify(stage, 'ACTIVITY_COMPLETED', 'INFO', 'PLANNING complete', 'Plan created')
        if (await awaitGate()) transitionTo('WORKSPACE')
        break
      }
      case 'WORKSPACE': {
        const workspace = await activities.prepareWorkspace({ metadata: metadata(stage), changeType, featureSlug: request.ticketId, version: CONTRACT_VERSION })
        workspaceRef = workspace.workspaceRef
        await notify(stage, 'ACTIVITY_COMPLETED', 'INFO', 'WORKSPACE complete', 'Workspace prepared')
        transitionTo('IMPLEMENTATION')
        break
      }
      case 'IMPLEMENTATION': {
        const implementation = await activities.implement({ metadata: metadata(stage), workspaceRef, implementationPlanRef, version: CONTRACT_VERSION })
        implementationArtifactRef = implementation.implementationArtifactRef
        await notify(stage, 'ACTIVITY_COMPLETED', 'INFO', 'IMPLEMENTATION complete', 'Implementation completed')
        transitionTo('QA')
        break
      }
      case 'QA': {
        const qa = await activities.runQualityAssurance({ metadata: metadata(stage), workspaceRef, implementationArtifactRef, version: CONTRACT_VERSION })
        await activities.recordAttemptHistory({
          metadata: metadata(stage),
          implementationArtifactRef,
          qaReportRef: qa.reportRef,
          qaScore: qa.score,
          status: qa.passed ? 'PASSED' : 'FAILED',
          version: CONTRACT_VERSION,
        })
        if (qa.score < minimumQaScore) {
          if (attemptNumber < maxAttempts) {
            attemptNumber += 1
            transitionTo('IMPLEMENTATION')
          } else {
            runStatus = 'FAILED'
            stage = undefined
          }
          break
        }
        if (await awaitGate()) {
          transitionTo('REVIEW_MERGE')
        } else if (runStatus === 'RUNNING') {
          attemptNumber += 1
        }
        break
      }
      case 'REVIEW_MERGE':
        await activities.manageSourceControl({ metadata: metadata(stage), workspaceRef, action: 'CREATE_DRAFT_PR', version: CONTRACT_VERSION })
        if (await awaitGate()) {
          await activities.manageSourceControl({ metadata: metadata(stage), workspaceRef, action: 'MERGE', version: CONTRACT_VERSION })
          runStatus = 'COMPLETED'
          stage = undefined
        }
        break
    }
  }

  return runStatus
}
