import type { ExecutionEvent, ExecutionStatus, ExecutionSubmission, WorkerCapabilities } from '@agentic-worker/contracts'

export interface PythonSessionClient {
  submit(submission: ExecutionSubmission): Promise<{ executionId: string }>
  status(executionId: string): Promise<ExecutionStatus>
  events(executionId: string, after: number): Promise<ExecutionEvent[]>
  cancel(executionId: string): Promise<ExecutionStatus>
  capabilities(): Promise<WorkerCapabilities>
}

export interface RegisteredPythonSession {
  sessionId: string
  healthy: () => boolean
  client: PythonSessionClient
}

export class UpstreamHttpError extends Error {
  constructor(readonly status: number) {
    super(`Python worker returned ${status}`)
  }
}

export class HttpPythonSessionClient implements PythonSessionClient {
  constructor(private readonly baseUrl: string, private readonly timeoutMs = 5_000) {}

  submit(submission: ExecutionSubmission): Promise<{ executionId: string }> {
    return this.request('/v1/executions', { method: 'POST', body: JSON.stringify(submission) }) as Promise<{ executionId: string }>
  }

  status(executionId: string): Promise<ExecutionStatus> {
    return this.request(`/v1/executions/${encodeURIComponent(executionId)}`) as Promise<ExecutionStatus>
  }

  events(executionId: string, after: number): Promise<ExecutionEvent[]> {
    return this.request(`/v1/executions/${encodeURIComponent(executionId)}/events?after=${after}`) as Promise<ExecutionEvent[]>
  }

  cancel(executionId: string): Promise<ExecutionStatus> {
    return this.request(`/v1/executions/${encodeURIComponent(executionId)}:cancel`, { method: 'POST' }) as Promise<ExecutionStatus>
  }

  capabilities(): Promise<WorkerCapabilities> {
    return this.request('/v1/capabilities') as Promise<WorkerCapabilities>
  }

  private async request(path: string, init: RequestInit = {}): Promise<unknown> {
    const response = await fetch(new URL(path, this.baseUrl), { ...init, signal: AbortSignal.timeout(this.timeoutMs), headers: { accept: 'application/json', ...(init.body ? { 'content-type': 'application/json' } : {}) } })
    if (!response.ok) throw new UpstreamHttpError(response.status)
    return response.json()
  }
}

export class PythonSessionRegistry {
  private readonly sessions = new Map<string, RegisteredPythonSession>()
  private nextSession = 0

  register(session: RegisteredPythonSession): void {
    this.sessions.set(session.sessionId, session)
  }

  get(sessionId: string): RegisteredPythonSession | undefined {
    return this.sessions.get(sessionId)
  }

  chooseHealthy(): RegisteredPythonSession | undefined {
    const healthy = [...this.sessions.values()].filter((session) => session.healthy())
    if (!healthy.length) return undefined
    const session = healthy[this.nextSession % healthy.length]
    this.nextSession += 1
    return session
  }

  healthySessions(): RegisteredPythonSession[] {
    return [...this.sessions.values()].filter((session) => session.healthy())
  }
}
