import test from 'node:test'
import assert from 'node:assert/strict'
import * as operatorConsole from '../src/lib/operator-console.js'
import {
  groupIssuesByLane,
  closeDrawerAfterRoute,
  shouldCloseExecutionDrawer,
  mergeNotification,
  operatorLanes,
  projectIssueCounts,
  validateDecision,
} from '../src/lib/operator-console.js'

test('closes the mobile navigation after a route is selected', () => {
  assert.equal(closeDrawerAfterRoute(true), false)
  assert.equal(closeDrawerAfterRoute(false), false)
})

test('closes an execution drawer only for Escape', () => {
  assert.equal(shouldCloseExecutionDrawer('Escape'), true)
  assert.equal(shouldCloseExecutionDrawer('Enter'), false)
})

test('groups every issue into exactly one operator lane', () => {
  const issues = [
    { id: '1', status: 'DONE' },
    { id: '2', status: 'CANCELLED' },
    { id: '3', status: 'FAILED' },
    { id: '4', status: 'PAUSED' },
    { id: '5', status: 'REJECTED' },
    { id: '6', status: 'IN_REVIEW' },
    { id: '7', status: 'IN_PROGRESS', workflowStage: 'QA' },
    { id: '8', status: 'OPEN' },
    { id: '9', status: 'IN_PROGRESS' },
  ]

  const grouped = groupIssuesByLane(issues)

  assert.deepEqual(operatorLanes.map((lane) => lane.id), ['approval', 'development', 'qa', 'review', 'revision', 'done'])
  assert.deepEqual(grouped.done.map((issue) => issue.id), ['1', '2'])
  assert.deepEqual(grouped.revision.map((issue) => issue.id), ['3', '4', '5'])
  assert.deepEqual(grouped.review.map((issue) => issue.id), ['6'])
  assert.deepEqual(grouped.qa.map((issue) => issue.id), ['7'])
  assert.deepEqual(grouped.approval.map((issue) => issue.id), [])
  assert.deepEqual(grouped.development.map((issue) => issue.id), ['8', '9'])
  assert.equal(Object.values(grouped).flat().length, issues.length)
})

test('counts open tickets and action-required tickets from issue status', () => {
  assert.deepEqual(projectIssueCounts([
    { status: 'OPEN' }, { status: 'IN_PROGRESS' }, { status: 'IN_REVIEW' }, { status: 'FAILED' }, { status: 'DONE' },
  ]), { open: 4, actionRequired: 2 })
})

test('keeps the operator lane labels in the approved order', () => {
  assert.deepEqual(operatorLanes.map((lane) => lane.label), [
    '승인 대기', '개발 중', 'QA', '리뷰·병합', '수정 필요', '완료',
  ])
})

test('prepends only a notification event that is not already present', () => {
  const items = [{ eventId: 'evt-1' }]

  assert.deepEqual(mergeNotification(items, { eventId: 'evt-2' }), [{ eventId: 'evt-2' }, ...items])
  assert.equal(mergeNotification(items, { eventId: 'evt-1' }), items)
  assert.deepEqual(mergeNotification([{}], {}), [{}, {}])
})

test('counts notifications without a read timestamp as unread', () => {
  assert.equal(typeof operatorConsole.unreadNotificationCount, 'function')
  assert.equal(operatorConsole.unreadNotificationCount([{ readAt: null }, { readAt: '2026-07-19T10:00:00Z' }, {}]), 2)
})

test('requires a target and reason for rejection decisions', () => {
  assert.deepEqual(validateDecision('REJECT', '사유', ''), { error: '반려 대상 단계를 선택하세요.' })
  assert.deepEqual(validateDecision('REJECT', ' ', 'QA'), { error: '사유를 입력하세요.' })
  assert.deepEqual(validateDecision('REQUEST_REVISION', '', null), { error: '사유를 입력하세요.' })
  assert.deepEqual(validateDecision('REQUEST_REVISION', null, null), { error: '사유를 입력하세요.' })
  assert.deepEqual(validateDecision('APPROVE', '', null), { error: null })
})
