import type { ProjectExecutionSnapshot } from '@agentic-worker/contracts'
import { NativeConnection } from '@temporalio/worker'

import { localEngineActivities } from './activities/local-engine-activities.js'
import { HttpGatewayClient } from './gateway-client.js'
import { createGatewayAgentWorker } from './worker.js'

const gatewayUrl = process.env.GATEWAY_URL ?? 'http://localhost:3001'
const temporalAddress = process.env.TEMPORAL_ADDRESS ?? 'localhost:7233'

// Project snapshot for the run. Contract-safe: credential-free remote URI, no secrets, no local paths.
// Configurable so a local integration run can point at a locally-served remote (e.g. git daemon).
const project: ProjectExecutionSnapshot = {
  projectId: process.env.PROJECT_ID ?? 'local-integration',
  repositoryUri: process.env.PROJECT_REPOSITORY_URI ?? 'https://github.com/acme/local-integration.git',
  baseBranch: process.env.PROJECT_BASE_BRANCH ?? 'main',
  credentialRef: null,
  requestedSourceCommit: null,
}

async function main(): Promise<void> {
  const connection = await NativeConnection.connect({ address: temporalAddress })
  try {
    const worker = await createGatewayAgentWorker({
      connection,
      namespace: 'default',
      gateway: new HttpGatewayClient(gatewayUrl),
      project,
      localActivities: localEngineActivities,
    })
    console.log(`temporal-worker connected: temporal=${temporalAddress} gateway=${gatewayUrl}`)
    await worker.run()
  } finally {
    await connection.close()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
