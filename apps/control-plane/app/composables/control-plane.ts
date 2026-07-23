import { ref, type Ref } from 'vue'

export interface AuthInput {
  email: string
  password: string
  name?: string
}

export interface CreateProjectInput {
  name: string
  repositoryUri: string
  baseBranch?: string
  credentialRef?: string
}

export interface CreateIssueInput {
  title: string
  description?: string
  priority?: string
}

type FetchOptions = {
  method?: 'POST'
  body?: unknown
  credentials: 'include'
}

export type ControlPlaneFetcher = <T>(path: string, options: FetchOptions) => Promise<T>

const fetchWithSession: ControlPlaneFetcher = (path, options) => $fetch(path, {
  ...options,
  body: options.body as Record<string, unknown> | undefined,
})

export const createControlPlaneApi = (fetcher: ControlPlaneFetcher = fetchWithSession) => ({
  register: <T>(input: AuthInput) => fetcher<T>('/api/auth/register', { method: 'POST', body: input, credentials: 'include' }),
  login: <T>(input: AuthInput) => fetcher<T>('/api/auth/login', { method: 'POST', body: input, credentials: 'include' }),
  listProjects: <T>() => fetcher<T>('/api/projects', { credentials: 'include' }),
  getProject: <T>(id: string) => fetcher<T>(`/api/projects/${id}`, { credentials: 'include' }),
  createProject: <T>(input: CreateProjectInput) => fetcher<T>('/api/projects', { method: 'POST', body: input, credentials: 'include' }),
  listIssues: <T>(projectId: string) => fetcher<T>(`/api/projects/${projectId}/issues`, { credentials: 'include' }),
  getIssue: <T>(id: string) => fetcher<T>(`/api/issues/${id}`, { credentials: 'include' }),
  createIssue: <T>(projectId: string, input: CreateIssueInput) => fetcher<T>(`/api/projects/${projectId}/issues`, { method: 'POST', body: input, credentials: 'include' }),
})

export const useControlPlaneApi = () => createControlPlaneApi()

export function isUnauthenticatedError(cause: unknown): boolean {
  if (!cause || typeof cause !== 'object') return false
  const error = cause as { status?: number; statusCode?: number; response?: { status?: number } }
  return [error.status, error.statusCode, error.response?.status].some((status) => status === 401)
}

export const isIssueForProject = (issue: { projectId: string }, projectId: string) => issue.projectId === projectId

export const writeDraft = (storage: { setItem: (key: string, value: string) => void }, key: string, draft: string) => {
  try {
    storage.setItem(key, draft)
    return { success: true } as const
  } catch {
    return { success: false } as const
  }
}

export type MockWorkerEvent = {
  id: string
  issueId: string
  status: 'pending' | 'working' | 'rejected'
  message: string
}

export type MockWorker = {
  events: Ref<MockWorkerEvent[]>
  advance: () => void
  reject: () => void
}

export const createMockWorker = (issueId: string): MockWorker => {
  const events = ref<MockWorkerEvent[]>([])
  let sequence = 0
  const append = (status: MockWorkerEvent['status'], message: string) => {
    events.value.push({ id: `${issueId}:${++sequence}`, issueId, status, message })
  }
  append('pending', 'Mock worker is waiting.')

  return {
    events,
    advance: () => append('working', 'Mock worker is working.'),
    reject: () => append('rejected', 'Mock worker feedback was rejected.'),
  }
}

export const useMockWorker = createMockWorker
