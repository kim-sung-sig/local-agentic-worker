<template>
  <div>
    <div v-if="loading" class="loading">불러오는 중...</div>
    <div v-else class="detail-card">

      <!-- 헤더 -->
      <div class="detail-meta">
        <span :class="priorityClass(issue.priority)" class="badge">{{ issue.priority }}</span>
        <span :class="statusClass(issue.status)" class="badge">{{ statusLabel(issue.status) }}</span>
        <span style="color:#9ca3af;font-size:12px">#{{ issue.issueNumber }}</span>
      </div>
      <div class="detail-title">{{ issue.title }}</div>
      <div class="detail-desc">{{ issue.description || '설명 없음' }}</div>

      <!-- ───────────────────────────────────────────── -->
      <!-- 실패 메시지 패널                               -->
      <!-- ───────────────────────────────────────────── -->
      <div v-if="issue.status === 'FAILED' && failedPhaseInfo" class="failure-panel">
        <div class="failure-title">❌ {{ failedPhaseInfo.phaseName }} 페이즈 실패</div>
        <div class="failure-message">{{ failedPhaseInfo.errorMessage }}</div>
        <div class="failure-hint">Plan 시작 버튼으로 다시 시도할 수 있습니다.</div>
      </div>

      <!-- ───────────────────────────────────────────── -->
      <!-- 페이즈 컨트롤 패널                              -->
      <!-- ───────────────────────────────────────────── -->
      <div class="phase-panel">
        <div class="phase-panel-title">🤖 에이전트 페이즈 실행</div>

        <!-- 페이즈 진행 타임라인 -->
        <div class="phase-timeline">
          <div v-for="p in phaseTimeline" :key="p.phase" class="phase-step">
            <div :class="['phase-dot', p.dotClass]">{{ p.icon }}</div>
            <div class="phase-info">
              <div class="phase-name">{{ p.label }}</div>
              <div class="phase-sub">{{ p.sub }}</div>
            </div>
          </div>
        </div>

        <!-- 트리거 버튼 -->
        <div class="phase-actions">
          <button class="btn btn-phase" :disabled="phaseRunning || !canStartPlan"
                  @click="triggerPhase('plan')">
            📋 Plan 시작
          </button>
          <button class="btn btn-phase" :disabled="phaseRunning || !canStartDesign"
                  @click="triggerPhase('design')">
            🎨 Design 시작
          </button>
          <button class="btn btn-phase" :disabled="phaseRunning || !canStartDevelop"
                  @click="triggerPhase('develop')">
            🔧 Develop 시작
          </button>
          <button class="btn btn-phase btn-phase-combo" :disabled="phaseRunning || !canStartPlan"
                  @click="triggerPhase('plan-design')">
            ⚡ Plan + Design
          </button>
        </div>
        <p v-if="phaseError" class="error-msg">{{ phaseError }}</p>
      </div>

      <!-- ───────────────────────────────────────────── -->
      <!-- 문서 뷰어 탭                                   -->
      <!-- ───────────────────────────────────────────── -->
      <div v-if="hasPlanDoc || hasDesignDoc" class="doc-panel">
        <div class="doc-tabs">
          <button v-if="hasPlanDoc"
                  :class="['doc-tab', activeDocTab === 'plan' ? 'doc-tab-active' : '']"
                  @click="showDoc('plan')">📄 Plan 문서</button>
          <button v-if="hasDesignDoc"
                  :class="['doc-tab', activeDocTab === 'design' ? 'doc-tab-active' : '']"
                  @click="showDoc('design')">📐 Design 문서</button>
        </div>
        <div v-if="activeDocTab && currentDoc" class="doc-content">
          <div class="doc-path">📁 {{ currentDoc.path }}</div>
          <div class="markdown-body" v-html="renderedDoc"></div>
        </div>
        <div v-if="docLoading" class="doc-loading">문서 불러오는 중...</div>
      </div>

      <!-- ───────────────────────────────────────────── -->
      <!-- 에이전트 실행 로그                              -->
      <!-- ───────────────────────────────────────────── -->
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

      <!-- ───────────────────────────────────────────── -->
      <!-- 리뷰 패널 (IN_REVIEW)                          -->
      <!-- ───────────────────────────────────────────── -->
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

      <!-- 하단 액션 -->
      <div class="detail-actions">
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
import { marked } from 'marked'
import { IssueApi, AgentPhaseApi } from '../api'

export default {
  data() {
    return {
      issue: null, loading: true, error: null,
      agentLogs: [], agentStatus: null,
      showFeedback: false, feedbackText: '',
      eventSource: null,
      // 페이즈
      phases: [],        // AgentJob 페이즈 목록
      phaseRunning: false,
      phaseError: null,
      // 문서 뷰어
      planDoc: null,
      designDoc: null,
      activeDocTab: null,
      docLoading: false,
    }
  },
  computed: {
    showAgentPanel() {
      return this.issue && [
        'PLAN_IN_PROGRESS', 'DESIGN_IN_PROGRESS', 'DEV_IN_PROGRESS', 'IN_REVIEW'
      ].includes(this.issue.status)
    },
    // 페이즈 전환 가능 여부
    canStartPlan()   { return ['OPEN', 'FAILED'].includes(this.issue?.status) },
    canStartDesign() { return ['PLAN_DONE'].includes(this.issue?.status) },
    canStartDevelop(){ return ['DESIGN_DONE', 'PLAN_DONE'].includes(this.issue?.status) },

    hasPlanDoc()   { return !!this.planDoc },
    hasDesignDoc() { return !!this.designDoc },

    currentDoc() {
      if (this.activeDocTab === 'plan')   return this.planDoc
      if (this.activeDocTab === 'design') return this.designDoc
      return null
    },
    renderedDoc() {
      return this.currentDoc ? marked.parse(this.currentDoc.content) : ''
    },

    /** 실패한 페이즈 정보 (errorMessage 포함) */
    failedPhaseInfo() {
      const labels = { PLAN: 'Plan', DESIGN: 'Design', DEVELOP: 'Develop' }
      const failed = [...this.phases]
        .filter(p => p.status === 'FAILED')
        .sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt))[0]
      if (!failed) return null
      return {
        phaseName: labels[failed.phase] || failed.phase,
        errorMessage: failed.errorMessage || '알 수 없는 오류',
      }
    },

    // 타임라인 표시용
    phaseTimeline() {
      const phaseJob = (phase) => {
        const jobs = this.phases.filter(p => p.phase === phase)
        return jobs.sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt))[0] || null
      }

      const dotClass = (job, inProgressStatus, doneStatus, s) => {
        if (job?.status === 'FAILED') return 'dot-failed'
        if (job?.status === 'SUCCEEDED') return 'dot-done'
        if (job?.status === 'PLANNING' || job?.status === 'CODING' || job?.status === 'VERIFYING') return 'dot-running'
        // 전체 이슈 상태로 폴백
        if (s === doneStatus || (['IN_REVIEW','CLOSED'].includes(s) && doneStatus !== 'IN_REVIEW')) return 'dot-done'
        if (s === inProgressStatus) return 'dot-running'
        return 'dot-pending'
      }

      const planJob   = phaseJob('PLAN')
      const designJob = phaseJob('DESIGN')
      const devJob    = phaseJob('DEVELOP')
      const s = this.issue?.status

      return [
        {
          phase: 'PLAN', label: 'Plan', icon: '📋',
          sub: planJob?.status === 'FAILED'
            ? '❌ 실패'
            : (this.hasPlanDoc ? '완료 ✓' : 'docs/01-plan/'),
          dotClass: dotClass(planJob, 'PLAN_IN_PROGRESS', 'PLAN_DONE', s),
        },
        {
          phase: 'DESIGN', label: 'Design', icon: '🎨',
          sub: designJob?.status === 'FAILED'
            ? '❌ 실패'
            : (this.hasDesignDoc ? '완료 ✓' : 'docs/02-design/'),
          dotClass: dotClass(designJob, 'DESIGN_IN_PROGRESS', 'DESIGN_DONE', s),
        },
        {
          phase: 'DEVELOP', label: 'Develop', icon: '🔧',
          sub: devJob?.status === 'FAILED'
            ? '❌ 실패'
            : (s === 'IN_REVIEW' ? 'PR 생성됨 ✓' : 'do + analysis'),
          dotClass: dotClass(devJob, 'DEV_IN_PROGRESS', 'IN_REVIEW', s),
        },
      ]
    },
  },
  async mounted() {
    try {
      const res = await IssueApi.get(this.$route.params.id)
      this.issue = res.data
      await Promise.all([this.loadPhases(), this.loadDocs(), this.loadAgentJob()])
    } finally {
      this.loading = false
    }
  },
  beforeUnmount() { this.closeStream() },
  methods: {
    // ── 페이즈 트리거 ──────────────────────────
    async triggerPhase(phase) {
      this.phaseError = null
      this.phaseRunning = true
      try {
        const map = {
          plan:        () => AgentPhaseApi.startPlan(this.issue.id),
          design:      () => AgentPhaseApi.startDesign(this.issue.id),
          develop:     () => AgentPhaseApi.startDevelop(this.issue.id),
          'plan-design': () => AgentPhaseApi.startPlanDesign(this.issue.id),
        }
        await map[phase]()
        // 상태 폴링 시작 (2초마다 새로고침)
        this.startPolling()
      } catch (e) {
        this.phaseError = e.response?.data?.message || `${phase} 실행 실패`
        this.phaseRunning = false
      }
    },

    // ── 상태 폴링 ──────────────────────────────
    startPolling() {
      const interval = setInterval(async () => {
        try {
          const res = await IssueApi.get(this.issue.id)
          const prev = this.issue.status
          this.issue = res.data
          // 완료 상태 감지
          const done = ['PLAN_DONE','DESIGN_DONE','IN_REVIEW','FAILED','CLOSED']
          if (done.includes(this.issue.status) && prev !== this.issue.status) {
            clearInterval(interval)
            this.phaseRunning = false
            await Promise.all([this.loadPhases(), this.loadDocs()])
            // FAILED면 스트림도 종료
            if (this.issue.status === 'FAILED') this.closeStream()
          }
        } catch (_) { clearInterval(interval); this.phaseRunning = false }
      }, 2000)
      // 최대 15분
      setTimeout(() => { clearInterval(interval); this.phaseRunning = false }, 15 * 60 * 1000)
    },

    // ── 문서 로딩 ──────────────────────────────
    async loadDocs() {
      this.docLoading = true
      try {
        const [planRes, designRes] = await Promise.allSettled([
          AgentPhaseApi.getPlanDocument(this.issue.id),
          AgentPhaseApi.getDesignDocument(this.issue.id),
        ])
        this.planDoc   = planRes.status   === 'fulfilled' ? planRes.value.data   : null
        this.designDoc = designRes.status === 'fulfilled' ? designRes.value.data : null
        // 기본 탭
        if (this.designDoc)    this.activeDocTab = 'design'
        else if (this.planDoc) this.activeDocTab = 'plan'
      } finally {
        this.docLoading = false
      }
    },
    async showDoc(tab) {
      this.activeDocTab = tab
    },

    // ── 페이즈 목록 ────────────────────────────
    async loadPhases() {
      try {
        const res = await AgentPhaseApi.getPhases(this.issue.id)
        this.phases = res.data
      } catch (_) {}
    },

    // ── 에이전트 로그 스트림 ───────────────────
    async loadAgentJob() {
      try {
        const res = await IssueApi.getAgentJob(this.issue.id)
        if (res.data?.id) this.connectStream(res.data.id)
      } catch (_) {}
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
            if (this.$refs.logContainer)
              this.$refs.logContainer.scrollTop = this.$refs.logContainer.scrollHeight
          })
        }
      }
      this.eventSource.addEventListener('done', () => this.closeStream())
      this.eventSource.onerror = () => this.closeStream()
    },
    closeStream() {
      if (this.eventSource) { this.eventSource.close(); this.eventSource = null }
    },

    // ── 리뷰 ───────────────────────────────────
    async approve() {
      try {
        await IssueApi.review(this.issue.id, { approved: true })
        this.issue.status = 'CLOSED'
        this.closeStream()
      } catch (e) { this.error = e.response?.data?.message || '승인 처리 실패' }
    },
    async reject() {
      try {
        await IssueApi.review(this.issue.id, { approved: false, feedback: this.feedbackText })
        this.issue.status = 'REJECTED'
        this.showFeedback = false
        this.feedbackText = ''
      } catch (e) { this.error = e.response?.data?.message || '반려 처리 실패' }
    },

    // ── 유틸 ───────────────────────────────────────────────
    statusLabel(s) {
      return {
        OPEN: 'OPEN', PLAN_IN_PROGRESS: 'PLAN 진행중', PLAN_DONE: 'PLAN 완료',
        DESIGN_IN_PROGRESS: 'DESIGN 진행중', DESIGN_DONE: 'DESIGN 완료',
        DEV_IN_PROGRESS: 'DEVELOP 진행중', IN_REVIEW: '검토중',
        REJECTED: '반려', FAILED: '실패', CLOSED: '완료',
      }[s] || s
    },
    statusClass(s) {
      return {
        OPEN: 'badge-open',
        PLAN_IN_PROGRESS: 'badge-progress', PLAN_DONE: 'badge-review',
        DESIGN_IN_PROGRESS: 'badge-progress', DESIGN_DONE: 'badge-review',
        DEV_IN_PROGRESS: 'badge-progress', IN_REVIEW: 'badge-review',
        REJECTED: 'badge-failed', FAILED: 'badge-failed', CLOSED: 'badge-closed',
      }[s] || ''
    },
    priorityClass(p) {
      return { LOW:'badge-low', MEDIUM:'badge-medium', HIGH:'badge-high', CRITICAL:'badge-critical' }[p] || ''
    },
    formatTime(ts) {
      return new Date(ts).toLocaleTimeString('ko-KR', { hour:'2-digit', minute:'2-digit', second:'2-digit' })
    },
    logTypeClass(type) {
      return { TOOL_USE:'log-tool', STATUS_CHANGE:'log-status', TEXT:'log-text' }[type] || 'log-text'
    },
  },
}
</script>

<style scoped>
/* ── 페이즈 패널 ────────────── */
.phase-panel {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 8px;
  padding: 16px;
  margin: 16px 0;
}
.phase-panel-title {
  font-weight: 600;
  color: #e2e8f0;
  margin-bottom: 14px;
  font-size: 14px;
}
.phase-timeline {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  overflow-x: auto;
}
.phase-step {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 120px;
  background: #0f172a;
  border-radius: 6px;
  padding: 10px 12px;
}
.phase-dot {
  font-size: 20px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #1e293b;
  border: 2px solid #334155;
  flex-shrink: 0;
}
.dot-done    { border-color: #22c55e; background: #14532d; }
.dot-running { border-color: #f59e0b; background: #451a03; animation: pulse 1s infinite; }
.dot-failed  { border-color: #ef4444; background: #450a0a; }
.dot-pending { border-color: #475569; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }

/* ── 실패 패널 ──────────────── */
.failure-panel {
  background: #1c0a0a;
  border: 1px solid #ef4444;
  border-radius: 8px;
  padding: 16px;
  margin: 12px 0;
}
.failure-title {
  font-weight: 700;
  color: #ef4444;
  font-size: 14px;
  margin-bottom: 8px;
}
.failure-message {
  color: #fca5a5;
  font-size: 13px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  background: #0f0505;
  border-radius: 4px;
  padding: 10px 12px;
  margin-bottom: 8px;
  max-height: 200px;
  overflow-y: auto;
}
.failure-hint {
  font-size: 12px;
  color: #6b7280;
}
.phase-name { font-size: 13px; font-weight: 600; color: #e2e8f0; }
.phase-sub  { font-size: 11px; color: #64748b; margin-top: 2px; }

.phase-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-phase {
  background: #334155;
  color: #e2e8f0;
  border: 1px solid #475569;
  border-radius: 6px;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-phase:hover:not(:disabled) { background: #475569; }
.btn-phase:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-phase-combo { border-color: #6366f1; color: #a5b4fc; }
.btn-phase-combo:hover:not(:disabled) { background: #312e81; }

/* ── 문서 뷰어 ──────────────── */
.doc-panel {
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  margin: 16px 0;
  overflow: hidden;
}
.doc-tabs {
  display: flex;
  border-bottom: 1px solid #334155;
  background: #1e293b;
}
.doc-tab {
  padding: 10px 16px;
  font-size: 13px;
  color: #94a3b8;
  background: none;
  border: none;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.doc-tab-active {
  color: #e2e8f0;
  border-bottom-color: #6366f1;
}
.doc-path {
  font-size: 11px;
  color: #64748b;
  padding: 8px 16px;
  border-bottom: 1px solid #1e293b;
  background: #0f172a;
  font-family: monospace;
}
.markdown-body {
  padding: 16px 20px;
  color: #cbd5e1;
  font-size: 13px;
  line-height: 1.7;
  max-height: 500px;
  overflow-y: auto;
}
.markdown-body :deep(h1) { color: #e2e8f0; font-size: 18px; margin: 12px 0 8px; }
.markdown-body :deep(h2) { color: #e2e8f0; font-size: 15px; margin: 10px 0 6px; border-bottom: 1px solid #334155; padding-bottom: 4px; }
.markdown-body :deep(h3) { color: #cbd5e1; font-size: 13px; margin: 8px 0 4px; }
.markdown-body :deep(table) { width: 100%; border-collapse: collapse; font-size: 12px; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #334155; padding: 6px 10px; }
.markdown-body :deep(th) { background: #1e293b; color: #e2e8f0; }
.markdown-body :deep(code) { background: #1e293b; padding: 1px 5px; border-radius: 3px; font-size: 12px; }
.markdown-body :deep(pre) { background: #1e293b; padding: 12px; border-radius: 6px; overflow-x: auto; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; }
.doc-loading { padding: 16px; color: #64748b; text-align: center; }
</style>
