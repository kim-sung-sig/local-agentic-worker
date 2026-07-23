import {
  ExecutionEventSchema,
  ExecutionSubmissionResultSchema,
  ExecutionStatusSchema,
  ExecutionSubmissionSchema,
  type ExecutionEvent,
  type ExecutionStatus,
  type ExecutionSubmission,
} from '@agentic-worker/contracts'
import { ApplicationFailure } from '@temporalio/activity'
import { z } from 'zod'

export class GatewayUnavailableError extends Error {
  readonly code = 'UNAVAILABLE'
  readonly retryable = true

  constructor() {
    super('UNAVAILABLE')
  }
}

export class GatewayNonRetryableError extends ApplicationFailure {
  readonly retryable = false

  constructor(readonly code: string) {
    super(code, `Gateway${code}`, true)
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
    return validate(ExecutionSubmissionResultSchema, await this.call('/v1/executions', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(submission) }))
  }

  async status(executionId: string): Promise<ExecutionStatus> {
    return validate(ExecutionStatusSchema, await this.call(`/v1/executions/${encodeURIComponent(executionId)}`))
  }

  async events(executionId: string, after = 0): Promise<ExecutionEvent[]> {
    const value = await this.call(`/v1/executions/${encodeURIComponent(executionId)}/events?after=${after}`)
    return validate(ExecutionEventSchema.array(), value)
  }

  private async call(path: string, init?: RequestInit): Promise<unknown> {
    let response: Response
    try {
      response = await this.request(new URL(path, this.baseUrl), init)
    } catch {
      throw new GatewayUnavailableError()
    }
    let value: unknown
    try {
      value = await response.json()
    } catch {
      if (response.status === 503 || response.ok) throw new GatewayUnavailableError()
      throw new GatewayNonRetryableError('INVALID_GATEWAY_RESPONSE')
    }
    if (!response.ok) throwGatewayError(response.status, value)
    return value
  }
}

const GatewayErrorSchema = z.object({ code: z.string().min(1), retryable: z.boolean() }).strict()

function throwGatewayError(status: number, value: unknown): never {
  const parsed = GatewayErrorSchema.safeParse(value)
  if (parsed.success) {
    if (parsed.data.retryable && parsed.data.code === 'UNAVAILABLE') throw new GatewayUnavailableError()
    if (!parsed.data.retryable) throw new GatewayNonRetryableError(parsed.data.code)
  }
  if (status === 503) throw new GatewayUnavailableError()
  throw new GatewayNonRetryableError('INVALID_GATEWAY_RESPONSE')
}

function validate<T>(schema: z.ZodType<T>, value: unknown): T {
  const parsed = schema.safeParse(value)
  if (!parsed.success) throw new GatewayUnavailableError()
  return parsed.data
}
