import {
  ExecutionStatusSchema,
  ExecutionSubmissionSchema,
  type ExecutionEvent,
  type ExecutionStatus,
  type ExecutionSubmission,
} from '@agentic-worker/contracts'

export class GatewayUnavailableError extends Error {
  readonly code = 'UNAVAILABLE'
  readonly retryable = true

  constructor() {
    super('UNAVAILABLE')
  }
}

export interface GatewayClient {
  submit(submission: ExecutionSubmission): Promise<{ executionId: string }>
  status(executionId: string): Promise<ExecutionStatus>
  events(executionId: string, after?: number): Promise<ExecutionEvent[]>
}

export class HttpGatewayClient implements GatewayClient {
  constructor(private readonly baseUrl: string, private readonly request: typeof fetch = fetch) {}

  async submit(submission: ExecutionSubmission): Promise<{ executionId: string }> {
    ExecutionSubmissionSchema.parse(submission)
    return this.call('/v1/executions', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(submission) }) as Promise<{ executionId: string }>
  }

  async status(executionId: string): Promise<ExecutionStatus> {
    return ExecutionStatusSchema.parse(await this.call(`/v1/executions/${encodeURIComponent(executionId)}`))
  }

  async events(executionId: string, after = 0): Promise<ExecutionEvent[]> {
    const value = await this.call(`/v1/executions/${encodeURIComponent(executionId)}/events?after=${after}`)
    return value as ExecutionEvent[]
  }

  private async call(path: string, init?: RequestInit): Promise<unknown> {
    let response: Response
    try {
      response = await this.request(new URL(path, this.baseUrl), init)
    } catch {
      throw new GatewayUnavailableError()
    }
    const value: unknown = await response.json()
    if (response.status === 503 && isUnavailable(value)) throw new GatewayUnavailableError()
    if (!response.ok) throw Object.assign(new Error(`Gateway request failed: ${response.status}`), value)
    return value
  }
}

function isUnavailable(value: unknown): value is { code: 'UNAVAILABLE'; retryable: true } {
  return Boolean(value && typeof value === 'object' && (value as { code?: unknown }).code === 'UNAVAILABLE' && (value as { retryable?: unknown }).retryable === true)
}
