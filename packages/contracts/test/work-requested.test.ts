import { describe, expect, it } from 'vitest'
import { WorkRequestedSchema, workflowIdFor } from '../src/work-requested.js'

describe('WorkRequested', () => {
  it('parses a valid work request', () => {
    const parsed = WorkRequestedSchema.parse({
      issueId: '3d6f0a1b-56ec-4350-9454-e33a55b21ad8',
      projectId: '11111111-1111-1111-1111-111111111111',
      repositoryUri: 'https://github.com/acme/catalog.git',
      baseBranch: 'main',
      rawSpecification: '상품 검색 개선',
      occurredAt: '2026-07-16T00:00:00Z',
    })

    expect(parsed.issueId).toBe('3d6f0a1b-56ec-4350-9454-e33a55b21ad8')
  })

  it('derives a deterministic workflow id from issueId', () => {
    expect(workflowIdFor('3d6f0a1b-56ec-4350-9454-e33a55b21ad8'))
      .toBe('issue-3d6f0a1b-56ec-4350-9454-e33a55b21ad8')
  })

  it('rejects a non-remote repository uri', () => {
    expect(() => WorkRequestedSchema.parse({
      issueId: '3d6f0a1b-56ec-4350-9454-e33a55b21ad8',
      projectId: '11111111-1111-1111-1111-111111111111',
      repositoryUri: 'file:///home/dev/repo',
      baseBranch: 'main',
      rawSpecification: 'spec',
      occurredAt: '2026-07-16T00:00:00Z',
    })).toThrow()
  })
})
