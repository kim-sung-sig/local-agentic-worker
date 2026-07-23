import { createGatewayHttpServer } from './http-server.js'
import { WorkerGateway } from './gateway.js'
import { HttpPythonSessionClient, PythonSessionRegistry } from './registry.js'

type SessionConfig = { sessionId: string, baseUrl: string }

function configuredSessions(env: NodeJS.ProcessEnv): SessionConfig[] {
  if (env.PYTHON_WORKER_SESSIONS) return JSON.parse(env.PYTHON_WORKER_SESSIONS) as SessionConfig[]
  if (env.PYTHON_WORKER_URL) return [{ sessionId: 'python-agent-worker', baseUrl: env.PYTHON_WORKER_URL }]
  throw new Error('Set PYTHON_WORKER_URL or PYTHON_WORKER_SESSIONS')
}

const registry = new PythonSessionRegistry()
for (const { sessionId, baseUrl } of configuredSessions(process.env)) registry.register({ sessionId, healthy: () => true, client: new HttpPythonSessionClient(baseUrl) })
const port = Number(process.env.PORT ?? '3001')
createGatewayHttpServer(new WorkerGateway(registry)).listen(port)
