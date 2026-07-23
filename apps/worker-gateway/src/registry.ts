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
