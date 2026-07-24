import { AgentWorkflowClient } from '../client.js'
import { driveIntakePlanning } from './drive-intake-planning.js'

const runId = process.argv[2] ?? `local-run-${Date.now()}`

driveIntakePlanning(new AgentWorkflowClient(), runId)
  .then((state) => {
    console.log(JSON.stringify({ runId, ...state }))
    process.exit(0)
  })
  .catch((error) => {
    console.error(error)
    process.exit(1)
  })
