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
    return { issue: null, loading: true, error: null }
  },
  computed: {
    transitions() {
      if (!this.issue) return []
      const map = {
        OPEN:        [{ status: 'IN_PROGRESS', label: '→ 진행 시작' }, { status: 'CLOSED', label: '닫기' }],
        IN_PROGRESS: [{ status: 'IN_REVIEW',   label: '→ 리뷰 요청' }, { status: 'FAILED', label: '→ 실패' }],
        IN_REVIEW:   [{ status: 'CLOSED',      label: '→ 완료'     }, { status: 'IN_PROGRESS', label: '→ 재작업' }],
        FAILED:      [{ status: 'IN_PROGRESS', label: '→ 재시도'   }, { status: 'CLOSED', label: '닫기' }],
        CLOSED:      [],
      }
      return map[this.issue.status] || []
    },
  },
  async mounted() {
    try {
      const res = await IssueApi.get(this.$route.params.id)
      this.issue = res.data
    } finally {
      this.loading = false
    }
  },
  methods: {
    async updateStatus(status) {
      this.error = null
      try {
        await IssueApi.updateStatus(this.issue.id, status)
        this.issue.status = status
      } catch (e) {
        this.error = e.response?.data?.message || '상태 변경 실패'
      }
    },
    statusClass(s) {
      return { OPEN: 'badge-open', IN_PROGRESS: 'badge-progress',
               IN_REVIEW: 'badge-review', FAILED: 'badge-failed', CLOSED: 'badge-closed' }[s] || ''
    },
    priorityClass(p) {
      return { LOW: 'badge-low', MEDIUM: 'badge-medium',
               HIGH: 'badge-high', CRITICAL: 'badge-critical' }[p] || ''
    },
  },
}
</script>
