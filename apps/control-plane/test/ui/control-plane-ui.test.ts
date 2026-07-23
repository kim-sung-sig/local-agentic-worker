import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { createServer, type ViteDevServer } from 'vite'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { createControlPlaneApi, createMockWorker, isIssueForProject, isUnauthenticatedError, writeDraft } from '../../app/composables/control-plane'

async function renderDashboardWith(projects: Array<{ id: string; name: string; repositoryUri: string | null; baseBranch: string }>, listIssues: () => Promise<unknown[]> = async () => []) {
  const fetcher = vi.fn(async (path: string) => {
    if (path === '/api/projects') return projects
    if (path.startsWith('/api/projects/') && path.endsWith('/issues')) return listIssues()
    throw new Error(`Unexpected API request: ${path}`)
  })
  const api = createControlPlaneApi(fetcher)
  const listed = await api.listProjects<typeof projects>()
  await Promise.all(listed.map((project) => api.listIssues(project.id)))
  const page = await vite.transformRequest('/app/pages/index.vue')
  await vite.waitForRequestsIdle('/app/pages/index.vue')
  return { fetcher, page }
}

async function renderPage(path: string) {
  const page = await vite.transformRequest(path)
  await vite.waitForRequestsIdle(path)
  expect(page?.code).toBeTruthy()
  return page?.code ?? ''
}

let vite: ViteDevServer

beforeAll(async () => {
  vite = await createServer({
    appType: 'custom',
    logLevel: 'error',
    plugins: [vue()],
    root: fileURLToPath(new URL('../..', import.meta.url)),
    server: { middlewareMode: true },
  })
})

afterAll(async () => vite.close())

describe('Control Plane UI client', () => {
  it('calls only an existing project endpoint with credentials', async () => {
    const fetcher = vi.fn().mockResolvedValue([])
    const api = createControlPlaneApi(fetcher)

    await api.listProjects()

    expect(fetcher).toHaveBeenCalledWith('/api/projects', { credentials: 'include' })
  })

  it('uses the mocked API boundary for registration and login', async () => {
    const fetcher = vi.fn().mockResolvedValue({})
    const api = createControlPlaneApi(fetcher)

    await api.register({ email: 'new@example.com', password: 'password-1', name: 'New user' })
    await api.login({ email: 'new@example.com', password: 'password-1' })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/auth/register', {
      method: 'POST',
      body: { email: 'new@example.com', password: 'password-1', name: 'New user' },
      credentials: 'include',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/auth/login', {
      method: 'POST',
      body: { email: 'new@example.com', password: 'password-1' },
      credentials: 'include',
    })
  })

  it('advances mock worker activity without making an HTTP request', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    const worker = createMockWorker('issue-1')

    try {
      worker.advance()
      worker.reject()
      worker.advance()

      expect(worker.events.value.at(-3)?.status).toBe('working')
      expect(worker.events.value.at(-2)?.status).toBe('rejected')
      expect(worker.events.value.at(-1)?.status).toBe('working')
      expect(new Set(worker.events.value.map((event) => event.id)).size).toBe(worker.events.value.length)
      expect(fetchSpy).not.toHaveBeenCalled()
    } finally {
      fetchSpy.mockRestore()
    }
  })

  it('recognizes unauthenticated API responses for the shell gate', () => {
    expect(isUnauthenticatedError({ statusCode: 401 })).toBe(true)
    expect(isUnauthenticatedError({ response: { status: 403 } })).toBe(false)
    expect(isUnauthenticatedError({ statusCode: 500 })).toBe(false)
  })

  it('compiles an unauthenticated dashboard gate with login and registration controls', async () => {
    const dashboard = await renderPage('/app/pages/index.vue')
    const authGate = await renderPage('/app/components/AuthGate.vue')

    expect(dashboard).toContain('AuthGate')
    expect(dashboard).toContain('unauthenticated')
    expect(authGate).toContain('Sign in')
    expect(authGate).toContain('Create your account')
    expect(authGate).toContain('Need an account? Register')
    expect(authGate).toMatch(/required:\s*""/)
  })

  it('compiles native required project fields and posts the validated project payload', async () => {
    const dashboard = await renderPage('/app/pages/index.vue')
    const fetcher = vi.fn().mockResolvedValue({})
    const api = createControlPlaneApi(fetcher)

    await api.createProject({ name: 'Control Plane', repositoryUri: 'https://github.com/acme/control-plane.git', baseBranch: 'main' })

    expect(dashboard).toMatch(/required:\s*""/)
    expect(dashboard).toContain('maxlength: "100"')
    expect(dashboard).toContain('createProject')
    expect(fetcher).toHaveBeenCalledWith('/api/projects', {
      method: 'POST',
      body: { name: 'Control Plane', repositoryUri: 'https://github.com/acme/control-plane.git', baseBranch: 'main' },
      credentials: 'include',
    })
  })

  it('rejects an issue returned for a different project', () => {
    expect(isIssueForProject({ projectId: 'p1' }, 'p1')).toBe(true)
    expect(isIssueForProject({ projectId: 'p2' }, 'p1')).toBe(false)
  })

  it('returns a failure result when draft storage is unavailable', () => {
    expect(writeDraft({ setItem: () => { throw new Error('quota exceeded') } }, 'issue-1', 'draft')).toEqual({ success: false })
  })

  it('renders a project returned by the Control Plane API and links to its board', async () => {
    const dashboard = await renderDashboardWith([{ id: 'p1', name: 'Control Plane', repositoryUri: null, baseBranch: 'main' }])

    expect(dashboard.page?.code).toContain('to: `/projects/${project.id}`')
    expect(dashboard.fetcher).toHaveBeenCalledWith('/api/projects/p1/issues', { credentials: 'include' })
  })

  it('compiles project issues and the selected issue action to their workspace route', async () => {
    const board = await renderPage('/app/pages/projects/[projectId].vue')

    expect(board).toContain('/projects/${$setup.projectId}/issues/${issue.id}')
    expect(board).toContain('/projects/${$setup.projectId}/issues/${$setup.selectedIssue.id}')
  })

  it('renders a nested workspace through NuxtPage instead of the project board', async () => {
    const board = await renderPage('/app/pages/projects/[projectId].vue')

    expect(board).toContain('_component_NuxtPage')
    expect(board).toContain('$setup.isWorkspaceRoute')
  })

  it('keeps an issue count failure recoverable instead of treating it as an empty project', async () => {
    const page = await renderPage('/app/pages/index.vue')

    expect(page).toContain('state.value = "error"')
    expect(page).not.toContain('issues: []')
  })

  it('labels the worker panel as mock and never exposes a worker HTTP endpoint', async () => {
    const workspace = await renderPage('/app/pages/projects/[projectId]/issues/[issueId].vue')

    expect(workspace).toContain('Mock worker')
    expect(workspace).toContain("worker.value.advance()")
    expect(workspace).toContain("worker.value.reject()")
    expect(workspace).toContain('loadToken')
    expect(workspace).toContain('isIssueForProject')
    expect(workspace).toContain('runMock("retry")')
    expect(workspace).toContain('계획 초안을 브라우저에 저장하지 못했습니다.')
    expect(workspace).not.toMatch(/\bfetch\s*\(|\/temporal|documents/)
    expect(workspace).not.toContain('/agent/')
  })
})
