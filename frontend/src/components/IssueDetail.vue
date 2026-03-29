<template>
  <div>
    <div v-if="loading" class="loading">불러오는 중...</div>
    <div v-else class="detail-card">
      <div class="detail-meta">
        <span :class="priorityClass(issue.priority)" class="badge">{{ issue.priority }}</span>
        <span :class="statusClass(issue.status)" class="badge">{{ issue.status }}</span>
        <span style="color:#9ca3af;font-size:12px">#{{ issue.issueNumber }}</span>
      </div>
      <div class="detail-title">{{ issue.title }}</div>
      <div class="detail-desc">{{ issue.description || '설명 없음' }}</div>

      <!-- 에이전트 작업 로그 패널 -->
      <div v-if="showAgentPanel" class="agent-panel">
        <div class="agent-panel-header">
          에이전트 작업 로그
          <span v-if="agentStatus" class="badge badge-progress" style="margin-left:8px">{{ agentStatus }}</span>
        </div>
        <div class="agent-logs" ref="logContainer">
          <div v-for="(log, i) in agentLogs" :key="i" class="agent-log-line">
            <span class="log-time">{{ formatTime(log.timestamp) }}</span>
            <span :class="logTypeClass(log.type)">{{ log.content }}</span>
          </div>
          <div v-if="agentLogs.length === 0" style="color:#6b7280">대기 중...</div>
        </div>
      </div>

      <!-- 리뷰 패널 (IN_REVIEW 상태에서만) -->
      <div v-if="issue.status === 'IN_REVIEW'" class="review-panel">
        <div v-if="issue.prUrl" style="margin-bottom:12px">
          PR: <a :href="issue.prUrl" target="_blank" class="pr-link">{{ issue.prUrl }}</a>
        </div>
        <div v-if="!showFeedback" class="review-actions">
          <button class="btn btn-primary" @click="approve">승인</button>
          <button class="btn btn-danger" @click="showFeedback = true">반려</button>
        </div>
        <div v-else class="feedback-form">
          <textarea v-model="feedbackText" class="feedback-input"
                    placeholder="반려 사유를 입력하세요..." rows="3" />
          <div style="margin-top:8px">
            <button class="btn btn-danger" @click="reject">반려 확정</button>
            <button class="btn btn-secondary" style="margin-left:8px"
                    @click="showFeedback = false">취소</button>
          </div>
        </div>
      </div>

      <div class="detail-actions">
        <button v-for="t in transitions" :key="t.status"
                class="btn btn-secondary btn-sm" @click="updateStatus(t.status)">
          {{ t.label }}
        </button>
        <button class="btn btn-secondary btn-sm"
                @click="$router.push('/projects/' + issue.projectId)">
          ← 목록
        </button>
      </div>
      <p v-if="error" class="error-msg" style="margin-top:12px">{{ error }}</p>
    </div>
  </div>
</template>

<script>
import { IssueApi } from '../api'

export default {
  data() {
    return {
      issue: null, loading: true, error: null,
      agentLogs: [], agentStatus: null,
      showFeedback: false, feedbackText: '',
      eventSource: null,
    }
  },
  computed: {
    showAgentPanel() {
      return this.issue && ['IN_PROGRESS', 'IN_REVIEW'].includes(this.issue.status)
    },
    transitions() {
      if (!this.issue) return []
      const map = {
        OPEN:        [{ status: 'IN_PROGRESS', label: '→ 진행 시작' }, { status: 'CLOSED', label: '닫기' }],
        IN_PROGRESS: [{ status: 'IN_REVIEW',   label: '→ 리뷰 요청' }, { status: 'FAILED', label: '→ 실패' }],
        IN_REVIEW:   [{ status: 'IN_PROGRESS', label: '→ 재작업' }],
        REJECTED:    [{ status: 'IN_PROGRESS', label: '→ 재시도' }],
        FAILED:      [{ status: 'IN_PROGRESS', label: '→ 재시도' }, { status: 'CLOSED', label: '닫기' }],
        CLOSED:      [],
      }
      return map[this.issue.status] || []
    },
  },
  async mounted() {
    try {
      const res = await IssueApi.get(this.$route.params.id)
      this.issue = res.data
      await this.loadAgentJob()
    } finally {
      this.loading = false
    }
  },
  beforeUnmount() {
    this.closeStream()
  },
  methods: {
    async loadAgentJob() {
      try {
        const res = await IssueApi.getAgentJob(this.issue.id)
        if (res.data?.id) {
          this.connectStream(res.data.id)
        }
      } catch (_) { /* AgentJob 없을 수 있음 */ }
    },
    connectStream(jobId) {
      this.closeStream()
      this.eventSource = new EventSource(`/api/agent-jobs/${jobId}/stream`)
      this.eventSource.onmessage = (e) => {
        const log = JSON.parse(e.data)
        if (log.type === 'STATUS_CHANGE') {
          this.agentStatus = log.content
        } else {
          this.agentLogs.push(log)
          this.$nextTick(() => {
            if (this.$refs.logContainer) {
              this.$refs.logContainer.scrollTop = this.$refs.logContainer.scrollHeight
            }
          })
        }
      }
      this.eventSource.addEventListener('done', () => this.closeStream())
      this.eventSource.onerror = () => this.closeStream()
    },
    closeStream() {
      if (this.eventSource) {
        this.eventSource.close()
        this.eventSource = null
      }
    },
    async approve() {
      this.error = null
      try {
        await IssueApi.review(this.issue.id, { approved: true })
        this.issue.status = 'CLOSED'
        this.closeStream()
      } catch (e) {
        this.error = e.response?.data?.message || '승인 처리 실패'
      }
    },
    async reject() {
      this.error = null
      try {
        await IssueApi.review(this.issue.id, { approved: false, feedback: this.feedbackText })
        this.issue.status = 'REJECTED'
        this.showFeedback = false
        this.feedbackText = ''
      } catch (e) {
        this.error = e.response?.data?.message || '반려 처리 실패'
      }
    },
    async updateStatus(status) {
      this.error = null
      try {
        await IssueApi.updateStatus(this.issue.id, status)
        this.issue.status = status
      } catch (e) {
        this.error = e.response?.data?.message || '상태 변경 실패'
      }
    },
    formatTime(ts) {
      return new Date(ts).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    },
    logTypeClass(type) {
      return { TOOL_USE: 'log-tool', STATUS_CHANGE: 'log-status', TEXT: 'log-text' }[type] || 'log-text'
    },
    statusClass(s) {
      return { OPEN: 'badge-open', IN_PROGRESS: 'badge-progress',
               IN_REVIEW: 'badge-review', REJECTED: 'badge-failed',
               FAILED: 'badge-failed', CLOSED: 'badge-closed' }[s] || ''
    },
    priorityClass(p) {
      return { LOW: 'badge-low', MEDIUM: 'badge-medium',
               HIGH: 'badge-high', CRITICAL: 'badge-critical' }[p] || ''
    },
  },
}
</script>
