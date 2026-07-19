<template>
  <section class="workflow-console">
    <div class="page-header">
      <div>
        <h2 class="page-title">워크플로 실행</h2>
        <p class="workflow-subtitle">티켓별 자동 개발 진행 상황과 승인 대기를 확인합니다.</p>
      </div>
    </div>

    <div class="workflow-filters">
      <label>
        <span class="sr-only">티켓 또는 실행 ID 검색</span>
        <input v-model="query" type="search" placeholder="티켓 또는 실행 ID 검색">
      </label>
      <label>
        <span class="sr-only">실행 상태 필터</span>
        <select v-model="statusFilter">
          <option value="">전체 상태</option>
          <option v-for="status in statuses" :key="status" :value="status">{{ statusLabel(status) }}</option>
        </select>
      </label>
    </div>

    <div class="workflow-layout">
      <div class="workflow-list" aria-label="Workflow Run 목록">
        <div class="workflow-list-heading">{{ filteredRuns.length }}개 실행</div>
        <div class="workflow-table-wrap">
          <table class="table workflow-table">
            <thead>
              <tr><th>티켓</th><th>현재 단계</th><th>상태</th><th>최신 QA</th><th>시도</th></tr>
            </thead>
            <tbody>
              <tr v-for="run in filteredRuns" :key="run.workflowRunId" :class="{ selected: run.workflowRunId === selectedRunId }" @click="selectedRunId = run.workflowRunId">
                <td><strong>{{ run.ticketId }}</strong><small>{{ shortId(run.workflowRunId) }}</small></td>
                <td>{{ stageLabel(run.currentStage) }}</td>
                <td><span class="badge" :class="statusClass(run.status)">{{ statusLabel(run.status) }}</span></td>
                <td>{{ latestAttempt(run)?.qaScore ?? '-' }}</td>
                <td>{{ run.attempts.length }}/{{ maxAttempts }}</td>
              </tr>
              <tr v-if="filteredRuns.length === 0"><td colspan="5" class="empty-state">조건과 일치하는 실행이 없습니다.</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <aside v-if="selectedRun" class="workflow-detail" aria-label="선택한 Workflow Run 상세">
        <div class="workflow-detail-header">
          <div><strong>{{ selectedRun.ticketId }}</strong><small>{{ selectedRun.workflowRunId }}</small></div>
          <span class="badge" :class="statusClass(selectedRun.status)">{{ statusLabel(selectedRun.status) }}</span>
        </div>

        <ol class="workflow-timeline">
          <li v-for="(stage, index) in workflowStages" :key="stage" :class="timelineClass(stage, index)">
            <span class="timeline-index">{{ index + 1 }}</span>
            <span>{{ stageLabel(stage) }}</span>
          </li>
        </ol>

        <section class="workflow-actions">
          <h3>승인 및 액션</h3>
          <p v-if="actionMessage" class="action-message">{{ actionMessage }}</p>
          <label v-if="canRequestRevision || canReject" class="workflow-reason">
            <span>사유</span>
            <textarea v-model="reason" rows="2" placeholder="수정 또는 반려 사유를 입력하세요."></textarea>
          </label>
          <label v-if="canReject" class="workflow-reason">
            <span>반려 대상 단계</span>
            <select v-model="rejectionTarget">
              <option value="">대상 단계를 선택하세요.</option>
              <option v-for="stage in rejectionStages" :key="stage" :value="stage">{{ stageLabel(stage) }}</option>
            </select>
          </label>
          <div class="workflow-action-buttons">
            <button v-if="canApprove" class="btn btn-primary" @click="decide('APPROVE')">승인</button>
            <button v-if="canRequestRevision" class="btn btn-secondary" @click="decide('REQUEST_REVISION')">수정 요청</button>
            <button v-if="canReject" class="btn btn-danger" @click="decide('REJECT')">반려</button>
            <button v-if="canRetry" class="btn btn-primary" @click="decide('RETRY')">재시도</button>
            <button v-if="canCancel" class="btn btn-secondary" @click="decide('CANCEL')">취소</button>
          </div>
        </section>

        <section class="attempt-history">
          <h3>시도 이력</h3>
          <table class="table">
            <thead><tr><th>시도</th><th>QA 점수</th><th>상태</th><th>완료</th></tr></thead>
            <tbody>
              <tr v-for="attempt in selectedRun.attempts" :key="attempt.attemptNumber">
                <td>{{ attempt.attemptNumber }}</td><td>{{ attempt.qaScore ?? '-' }}</td><td>{{ attempt.status }}</td><td>{{ formatDate(attempt.finishedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </aside>
    </div>
  </section>
</template>

<script>
import { applyDecision, filterRuns, mockWorkflowRuns, workflowStages } from '../lib/workflow-console'

const GATE_STAGES = ['INTAKE', 'PLANNING', 'QA', 'REVIEW_MERGE']

export default {
  data() {
    return {
      runs: mockWorkflowRuns.map((run) => ({ ...run, attempts: [...run.attempts] })),
      selectedRunId: mockWorkflowRuns[0].workflowRunId,
      query: '', statusFilter: '', reason: '', rejectionTarget: '', actionMessage: '',
      workflowStages, statuses: ['RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED'], maxAttempts: 3,
    }
  },
  computed: {
    filteredRuns() { return filterRuns(this.runs, this.query, this.statusFilter) },
    selectedRun() { return this.runs.find((run) => run.workflowRunId === this.selectedRunId) || this.filteredRuns[0] },
    isGateStage() { return this.selectedRun && GATE_STAGES.includes(this.selectedRun.currentStage) },
    canApprove() { return this.isGateStage && this.selectedRun.status === 'RUNNING' },
    canRequestRevision() { return this.isGateStage && this.selectedRun.status === 'RUNNING' },
    canReject() { return this.isGateStage && this.selectedRun.status === 'RUNNING' },
    canRetry() { return this.selectedRun?.status === 'PAUSED' },
    canCancel() { return ['RUNNING', 'PAUSED'].includes(this.selectedRun?.status) },
    rejectionStages() {
      return workflowStages.slice(0, workflowStages.indexOf(this.selectedRun.currentStage) + 1)
    },
  },
  methods: {
    latestAttempt(run) { return run.attempts.at(-1) },
    shortId(id) { return id.slice(0, 8) },
    formatDate(value) { return value ? new Date(value).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-' },
    stageLabel(stage) { return { INTAKE: '기획 정제', PLANNING: '구현 계획', WORKSPACE: '작업공간', IMPLEMENTATION: '구현', QA: 'QA', REVIEW_MERGE: '검토·병합' }[stage] },
    statusLabel(status) { return { RUNNING: '진행 중', PAUSED: '일시 정지', COMPLETED: '완료', FAILED: '실패', CANCELLED: '취소됨' }[status] },
    statusClass(status) { return { RUNNING: 'badge-progress', PAUSED: 'badge-review', COMPLETED: 'badge-closed', FAILED: 'badge-failed', CANCELLED: 'badge-closed' }[status] },
    timelineClass(stage, index) {
      const currentIndex = workflowStages.indexOf(this.selectedRun.currentStage)
      if (this.selectedRun.status === 'COMPLETED' || index < currentIndex) return 'done'
      if (stage === this.selectedRun.currentStage) return this.selectedRun.status === 'FAILED' ? 'failed' : 'current'
      return 'pending'
    },
    decide(decision) {
      if (['REQUEST_REVISION', 'REJECT'].includes(decision) && !this.reason.trim()) {
        this.actionMessage = '수정 또는 반려 사유를 입력하세요.'
        return
      }
      if (decision === 'REJECT' && !this.rejectionTarget) {
        this.actionMessage = '반려할 대상 단계를 선택하세요.'
        return
      }
      const index = this.runs.findIndex((run) => run.workflowRunId === this.selectedRun.workflowRunId)
      this.runs.splice(index, 1, applyDecision(this.selectedRun, decision, this.rejectionTarget))
      this.actionMessage = `${{ APPROVE: '승인', REQUEST_REVISION: '수정 요청', REJECT: '반려', RETRY: '재시도', CANCEL: '취소' }[decision]} 처리되었습니다.`
      this.reason = ''
      this.rejectionTarget = ''
    },
  },
}
</script>
