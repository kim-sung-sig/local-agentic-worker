export const operatorLanes = [
  { id: 'approval', label: '승인 대기' },
  { id: 'development', label: '개발 중' },
  { id: 'qa', label: 'QA' },
  { id: 'review', label: '리뷰·병합' },
  { id: 'revision', label: '수정 필요' },
  { id: 'done', label: '완료' },
]

export function groupIssuesByLane(issues) {
  const grouped = Object.fromEntries(operatorLanes.map(({ id }) => [id, []]))

  for (const issue of issues) {
    const lane = ['DONE', 'CANCELLED'].includes(issue.status)
      ? 'done'
      : ['FAILED', 'PAUSED', 'REJECTED'].includes(issue.status)
        ? 'revision'
        : issue.status === 'IN_REVIEW'
          ? 'review'
          : issue.workflowStage === 'QA'
            ? 'qa'
            : 'development'
    grouped[lane].push(issue)
  }

  return grouped
}

export function projectIssueCounts(issues) {
  return {
    open: issues.filter((issue) => !['DONE', 'CANCELLED', 'COMPLETED'].includes(issue.status)).length,
    actionRequired: issues.filter((issue) => ['IN_REVIEW', 'FAILED'].includes(issue.status)).length,
  }
}

export function mergeNotification(items, notification) {
  return notification.eventId && items.some((item) => item.eventId === notification.eventId) ? items : [notification, ...items]
}

export function unreadNotificationCount(items) {
  return items.filter((item) => !item.readAt).length
}

export function validateDecision(decision, reason, targetStage) {
  if (decision === 'REJECT' && !targetStage) return { error: '반려 대상 단계를 선택하세요.' }
  if (['REJECT', 'REQUEST_REVISION'].includes(decision) && !reason?.trim()) return { error: '사유를 입력하세요.' }
  return { error: null }
}
