<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { isIssueForProject, isUnauthenticatedError, useControlPlaneApi, useMockWorker, writeDraft } from '../../../../composables/control-plane'

type Project = { id: string; name: string; repositoryUri: string | null; baseBranch: string }
type Issue = { id: string; projectId: string; issueNumber: number; title: string; description: string | null; priority: string | null; status: string; createdAt: string }

const defaultPlan = `## 목표\n\n문제를 해결할 범위와 완료 기준을 작성하세요.\n\n## 세부 계획\n\n1. 현재 동작과 영향 범위를 확인합니다.\n2. 변경 방향과 검증 방법을 정리합니다.\n3. 검토 후 구현으로 넘깁니다.`
const api = useControlPlaneApi()
const route = useRoute()
const project = ref<Project | null>(null)
const issues = ref<Issue[]>([])
const issue = ref<Issue | null>(null)
const state = ref<'loading' | 'ready' | 'unauthenticated' | 'error'>('loading')
const error = ref('')
const query = ref('')
const plan = ref(defaultPlan)
const planState = ref('초안 · 이 브라우저에 저장되지 않음')
const toast = ref('')
const hasMounted = ref(false)
let loadToken = 0
const worker = shallowRef(useMockWorker(String(route.params.issueId)))
const projectId = computed(() => String(route.params.projectId))
const issueId = computed(() => String(route.params.issueId))
const workerEvents = computed(() => worker.value.events.value)
const visibleIssues = computed(() => {
  const search = query.value.toLowerCase()
  return issues.value.filter((item) => !search || `${item.issueNumber} ${item.title} ${item.description ?? ''}`.toLowerCase().includes(search))
})

function statusLabel(status: string) {
  return { OPEN: '계획 중', IN_PROGRESS: '구현 중', IN_REVIEW: '승인 대기', DONE: '완료', FAILED: '재작업 필요' }[status] ?? status
}

function priorityLabel(priority: string | null) {
  return { CRITICAL: '긴급', HIGH: '높음', MEDIUM: '보통', LOW: '낮음' }[priority ?? ''] ?? '보통'
}

function planKey() {
  return `agentic-worker:issue:${issueId.value}:plan`
}

function loadDraft() {
  if (!hasMounted.value) return
  const saved = localStorage.getItem(planKey())
  plan.value = saved ?? defaultPlan
  planState.value = saved === null ? '초안 · 이 브라우저에 저장되지 않음' : '저장된 계획 초안'
}

function saveDraft() {
  if (!hasMounted.value) return
  if (writeDraft(localStorage, planKey(), plan.value).success) {
    planState.value = '계획 초안을 이 브라우저에 저장했습니다.'
    toast.value = ''
  } else {
    planState.value = '계획 초안은 유지되었지만 저장하지 못했습니다.'
    toast.value = '계획 초안을 브라우저에 저장하지 못했습니다. 저장소 설정을 확인한 뒤 다시 시도하세요.'
  }
}

function runMock(action: 'approve' | 'reject' | 'retry') {
  if (action === 'reject') worker.value.reject()
  else worker.value.advance()
  toast.value = `Mock worker: ${action === 'approve' ? '승인' : action === 'reject' ? '거절' : '재시도'} 상태만 변경했습니다. Worker execution is mocked.`
}

async function loadWorkspace() {
  const token = ++loadToken
  const requestedProjectId = projectId.value
  const requestedIssueId = issueId.value
  state.value = 'loading'
  error.value = ''
  try {
    const [loadedProject, loadedIssues, loadedIssue] = await Promise.all([
      api.getProject<Project>(requestedProjectId),
      api.listIssues<Issue[]>(requestedProjectId),
      api.getIssue<Issue>(requestedIssueId),
    ])
    if (token !== loadToken || projectId.value !== requestedProjectId || issueId.value !== requestedIssueId) return
    if (!isIssueForProject(loadedIssue, requestedProjectId)) {
      error.value = '선택한 이슈가 현재 프로젝트에 속하지 않습니다.'
      state.value = 'error'
      return
    }
    project.value = loadedProject
    issues.value = loadedIssues
    issue.value = loadedIssue
    loadDraft()
    state.value = 'ready'
  } catch (cause: unknown) {
    if (token !== loadToken || projectId.value !== requestedProjectId || issueId.value !== requestedIssueId) return
    if (isUnauthenticatedError(cause)) {
      state.value = 'unauthenticated'
      return
    }
    error.value = cause instanceof Error ? cause.message : '이슈를 불러오지 못했습니다.'
    state.value = 'error'
  }
}

onMounted(() => {
  hasMounted.value = true
  loadWorkspace()
})

watch([projectId, issueId], () => {
  if (!hasMounted.value) return
  worker.value = useMockWorker(issueId.value)
  toast.value = ''
  loadWorkspace()
})
</script>

<template>
  <AuthGate v-if="state === 'unauthenticated'" @authenticated="loadWorkspace" />
  <section v-else class="issue-workspace" aria-labelledby="issue-title">
    <aside class="issue-rail workspace-rail" aria-label="이슈 목록">
      <header>
        <h2>이슈 목록</h2>
        <label>이슈 검색<input v-model.trim="query" type="search" placeholder="이슈 검색" /></label>
      </header>
      <p class="rail-total">총 {{ visibleIssues.length }}개</p>
      <div class="rail-list">
        <NuxtLink v-for="item in visibleIssues" :key="item.id" :class="{ active: item.id === issueId }" :to="`/projects/${projectId}/issues/${item.id}`">
          <small>E-{{ item.issueNumber }}</small><h3>{{ item.title }}</h3><p>{{ item.description || '이슈 설명이 없습니다.' }}</p><footer><span>{{ project?.name }}</span><b>{{ statusLabel(item.status) }}</b></footer>
        </NuxtLink>
        <p v-if="state === 'ready' && !visibleIssues.length">표시할 이슈가 없습니다.</p>
      </div>
    </aside>

    <p v-if="state === 'loading'" class="screen-message" role="status" aria-live="polite">이슈를 불러오는 중입니다.</p>
    <div v-else-if="state === 'error'" class="screen-message" role="alert"><p>{{ error }}</p><button class="button button-primary" type="button" @click="loadWorkspace">다시 시도</button></div>
    <template v-else-if="issue">
      <main class="issue-detail">
        <header class="detail-header">
          <p><NuxtLink :to="`/projects/${projectId}`">{{ project?.name ?? '프로젝트' }}</NuxtLink> <span>/</span> E-{{ issue.issueNumber }}</p>
          <h1 id="issue-title">{{ issue.title }}</h1>
          <div class="issue-meta"><span>상태: <b>{{ statusLabel(issue.status) }}</b></span><span>우선순위: <em>{{ priorityLabel(issue.priority) }}</em></span><span>생성일: {{ issue.createdAt.slice(0, 10) }}</span></div>
        </header>
        <section class="detail-card description-card"><h2>설명</h2><p>{{ issue.description || '이슈 설명이 없습니다.' }}</p></section>
        <section class="detail-card editor-card" aria-labelledby="plan-title">
          <header><div><h2 id="plan-title">계획 <small>초안</small></h2><p>AI 팀에 전달할 작업 범위를 계속 수정할 수 있습니다.</p></div></header>
          <label class="sr-only" for="draft-plan">작업 계획 편집</label>
          <textarea id="draft-plan" v-model="plan" rows="11" aria-label="작업 계획 편집"></textarea>
          <footer><span>{{ planState }}</span><button class="text-button" type="button" @click="saveDraft">저장</button></footer>
        </section>
      </main>

      <aside class="activity-panel" aria-labelledby="worker-title">
        <header><div><h2 id="worker-title">Mock worker</h2><small>Worker execution is mocked.</small></div><button class="button button-primary" type="button" @click="runMock('retry')">재시도</button></header>
        <ol class="activity-timeline"><li v-for="event in workerEvents" :key="event.id"><strong>{{ event.status }}</strong><p>{{ event.message }}</p></li></ol>
        <div class="approval-box"><h3>승인 대기</h3><p>이 작업공간의 승인과 거절은 화면 상태만 바꿉니다.</p><div><button class="button button-primary" type="button" @click="runMock('approve')">승인</button><button class="button button-danger" type="button" @click="runMock('reject')">거절</button></div></div>
      </aside>
    </template>
    <p v-if="toast" class="toast" role="status">{{ toast }}</p>
  </section>
</template>
