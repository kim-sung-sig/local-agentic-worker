import type { EngineActivities } from '@agentic-worker/contracts'

// The four execution methods are overridden by createGatewayEngineActivities;
// these bodies exist only to satisfy the EngineActivities shape.
export const localEngineActivities: EngineActivities = {
  assessTicket: async ({ version }) => ({ refinedSpecification: '', recommendedChangeType: 'FEATURE', version }),
  planImplementation: async ({ version }) => ({
    implementationPlanRef: { value: '', kind: 'PLAN', version },
    attemptPolicy: { minimumQaScore: 80, maxAttempts: 3, version },
    version,
  }),
  implement: async ({ version }) => ({ implementationArtifactRef: { value: '', kind: 'IMPLEMENTATION', version }, version }),
  runQualityAssurance: async ({ version }) => ({ passed: true, score: 100, reportRef: { value: '', kind: 'QA_REPORT', version }, version }),

  // Real local behaviour for stages the workflow invokes locally.
  recordAttemptHistory: async ({ version }) => ({ recorded: true, version }),
  sendNotification: async ({ version }) => ({ delivered: true, version }),

  // Deferred stages: never reached in the INTAKE→PLANNING integration path.
  prepareWorkspace: async () => {
    throw new Error('WORKSPACE stage is deferred in the local integration harness')
  },
  manageSourceControl: async () => {
    throw new Error('REVIEW_MERGE stage is deferred in the local integration harness')
  },
}
