import {
  ExecutionEventSchema,
  ExecutionStatusSchema,
  ExecutionSubmissionSchema,
  WorkerCapabilitiesSchema,
  type ExecutionEvent,
  type ExecutionStatus,
  type ExecutionSubmission,
  type WorkerCapabilities,
} from '@agentic-worker/contracts'
import { PythonSessionRegistry, type RegisteredPythonSession } from './registry.js'

export class GatewayError extends Error {
  constructor(readonly code: 'INVALID_ARGUMENT' | 'NOT_FOUND' | 'UNAVAILABLE', readonly retryable: boolean) {
    super(code)
  }
}

export class WorkerGateway {
  private readonly workflowSessions = new Map<string, string>()
  private readonly executionSessions = new Map<string, string>()

  constructor(private readonly registry: PythonSessionRegistry) {}

  async submit(value: unknown): Promise<{ executionId: string }> {
    const parsed = ExecutionSubmissionSchema.safeParse(value)
    if (!parsed.success) throw new GatewayError('INVALID_ARGUMENT', false)
    const session = this.sessionForWorkflow(parsed.data)
    const result = await this.call(session, () => session.client.submit(parsed.data))
    if (!result.executionId) throw new GatewayError('UNAVAILABLE', true)
    this.executionSessions.set(result.executionId, session.sessionId)
    return { executionId: result.executionId }
  }

  async status(executionId: string): Promise<ExecutionStatus> {
    return this.callForExecution(executionId, (session) => session.client.status(executionId), ExecutionStatusSchema.parse)
  }

  async events(executionId: string, after = 0): Promise<ExecutionEvent[]> {
    if (!Number.isInteger(after) || after < 0) throw new GatewayError('INVALID_ARGUMENT', false)
    return this.callForExecution(executionId, (session) => session.client.events(executionId, after), ExecutionEventSchema.array().parse)
  }

  async cancel(executionId: string): Promise<ExecutionStatus> {
    return this.callForExecution(executionId, (session) => session.client.cancel(executionId), ExecutionStatusSchema.parse)
  }

  async capabilities(): Promise<WorkerCapabilities[]> {
    return Promise.all(this.registry.healthySessions().map((session) => this.call(session, () => session.client.capabilities()).then(WorkerCapabilitiesSchema.parse)))
  }

  private sessionForWorkflow(submission: ExecutionSubmission): RegisteredPythonSession {
    const boundId = this.workflowSessions.get(submission.workflowRunId)
    if (boundId) return this.boundSession(boundId)
    const session = this.registry.chooseHealthy()
    if (!session) throw new GatewayError('UNAVAILABLE', true)
    this.workflowSessions.set(submission.workflowRunId, session.sessionId)
    return session
  }

  private async callForExecution<T>(executionId: string, operation: (session: RegisteredPythonSession) => Promise<unknown>, parse: (value: unknown) => T): Promise<T> {
    const sessionId = this.executionSessions.get(executionId)
    if (!sessionId) throw new GatewayError('NOT_FOUND', false)
    const session = this.boundSession(sessionId)
    return parse(await this.call(session, () => operation(session)))
  }

  private boundSession(sessionId: string): RegisteredPythonSession {
    const session = this.registry.get(sessionId)
    if (!session || !session.healthy()) throw new GatewayError('UNAVAILABLE', true)
    return session
  }

  private async call<T>(session: RegisteredPythonSession, operation: () => Promise<T>): Promise<T> {
    if (!session.healthy()) throw new GatewayError('UNAVAILABLE', true)
    try {
      return await operation()
    } catch {
      throw new GatewayError('UNAVAILABLE', true)
    }
  }
}
