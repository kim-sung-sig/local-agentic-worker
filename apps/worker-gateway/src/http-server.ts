import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { GatewayError, WorkerGateway } from './gateway.js'

async function body(request: IncomingMessage): Promise<unknown> {
  let data = ''
  for await (const chunk of request) data += chunk
  try {
    return JSON.parse(data)
  } catch {
    throw new GatewayError('INVALID_ARGUMENT', false)
  }
}

function reply(response: ServerResponse, status: number, value: unknown): void {
  response.writeHead(status, { 'content-type': 'application/json' })
  response.end(JSON.stringify(value))
}

function error(response: ServerResponse, value: unknown): void {
  if (value instanceof GatewayError) {
    reply(response, value.code === 'UNAVAILABLE' ? 503 : value.code === 'NOT_FOUND' ? 404 : 400, { code: value.code, retryable: value.retryable })
    return
  }
  reply(response, 500, { code: 'INTERNAL', retryable: false })
}

export function createGatewayHttpServer(gateway: WorkerGateway) {
  return createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? '/', 'http://gateway')
      const execution = /^\/v1\/executions\/([^/]+)$/.exec(url.pathname)
      const eventStream = /^\/v1\/executions\/([^/]+)\/events$/.exec(url.pathname)
      const cancellation = /^\/v1\/executions\/([^/]+):cancel$/.exec(url.pathname)
      if (request.method === 'POST' && url.pathname === '/v1/executions') return reply(response, 200, await gateway.submit(await body(request)))
      if (request.method === 'GET' && execution) return reply(response, 200, await gateway.status(decodeURIComponent(execution[1])))
      if (request.method === 'GET' && eventStream) return reply(response, 200, await gateway.events(decodeURIComponent(eventStream[1]), Number(url.searchParams.get('after') ?? '0')))
      if (request.method === 'POST' && cancellation) return reply(response, 200, await gateway.cancel(decodeURIComponent(cancellation[1])))
      if (request.method === 'GET' && url.pathname === '/v1/capabilities') return reply(response, 200, await gateway.capabilities())
      reply(response, 404, { code: 'NOT_FOUND', retryable: false })
    } catch (caught) {
      error(response, caught)
    }
  })
}
