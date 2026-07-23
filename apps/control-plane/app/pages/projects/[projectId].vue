<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { isUnauthenticatedError, useControlPlaneApi } from '../../composables/control-plane'

type Project = { id: string; name: string; repositoryUri: string | null; baseBranch: string }
type Issue = { id: string; projectId: string; issueNumber: number; title: string; description: string | null; priority: string | null; status: string }

const api = useControlPlaneApi()
const route = useRoute()
const project = ref<Project | null>(null)
const issues = ref<Issue[]>([])
const state = ref<'loading' | 'ready' | 'unauthenticated' | 'error'>('loading')
const error = ref('')
const query = ref('')
const status = ref('')
const showIssueForm = ref(false)
const saving = ref(false)
const formError = ref('')
const draft = reactive({ title: '', description: '', priority: 'MEDIUM' })
const hasMounted = ref(false)
const projectId = computed(() => String(route.params.projectId))
const isWorkspaceRoute = computed(() => Boolean(route.params.issueId))

const filteredIssues = computed(() => {
  const search = query.value.toLowerCase()
  return issues.value.filter((issue) => (!status.value || issue.status === status.value)
    && (!search || `${issue.issueNumber} ${issue.title} ${issue.description ?? ''}`.toLowerCase().includes(search)))
})
const selectedIssue = computed(() => filteredIssues.value[0] ?? issues.value[0])
const inProgressCount = computed(() => issues.value.filter((issue) => issue.status === 'IN_PROGRESS').length)
const reviewCount = computed(() => issues.value.filter((issue) => issue.status === 'IN_REVIEW').length)
const statusOptions = [{ value: 'OPEN', label: '계획 전' }, { value: 'IN_PROGRESS', label: '구현 중' }, { value: 'IN_REVIEW', label: '승인 대기' }, { value: 'DONE', label: '완료' }, { value: 'FAILED', label: '재작업 필요' }]

function statusLabel(value: string) {
  return statusOptions.find((option) => option.value === value)?.label ?? value
}

async function loadProject() {
  state.value = 'loading'
  error.value = ''
  try {
    const [loadedProject, loadedIssues] = await Promise.all([
      api.getProject<Project>(projectId.value),
      api.listIssues<Issue[]>(projectId.value),
    ])
    project.value = loadedProject
    issues.value = loadedIssues
    state.value = 'ready'
  } catch (cause: unknown) {
    if (isUnauthenticatedError(cause)) {
      state.value = 'unauthenticated'
      return
    }
    error.value = cause instanceof Error ? cause.message : '이슈를 불러오지 못했습니다.'
    state.value = 'error'
  }
}

async function createIssue() {
  saving.value = true
  formError.value = ''
  try {
    await api.createIssue(projectId.value, { title: draft.title, description: draft.description || undefined, priority: draft.priority })
    Object.assign(draft, { title: '', description: '', priority: 'MEDIUM' })
    showIssueForm.value = false
    await loadProject()
  } catch (cause: unknown) {
    if (isUnauthenticatedError(cause)) {
      state.value = 'unauthenticated'
      return
    }
    formError.value = '이슈를 등록하지 못했습니다. 서버 연결을 확인하세요.'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  hasMounted.value = true
  if (!isWorkspaceRoute.value) loadProject()
})
watch([projectId, isWorkspaceRoute], ([, workspace]) => {
  if (hasMounted.value && !workspace) loadProject()
})
</script>

<template>
  <NuxtPage v-if="isWorkspaceRoute" />
  <AuthGate v-else-if="state === 'unauthenticated'" @authenticated="loadProject" />
  <section v-else class="project-board" aria-labelledby="project-title">
    <aside class="issue-rail board-rail">
      <header>
        <p>프로젝트</p>
        <h1 id="project-title">{{ project?.name ?? '프로젝트' }}</h1>
        <small>{{ project?.repositoryUri ?? '저장소를 확인 중입니다.' }}</small>
      </header>
      <button class="rail-add" type="button" @click="showIssueForm = !showIssueForm">＋ 이슈 등록</button>
      <form v-if="showIssueForm" class="rail-form" @submit.prevent="createIssue">
        <label>이슈 제목<input v-model.trim="draft.title" required placeholder="이슈 제목" /></label>
        <label>이슈 내용<textarea v-model.trim="draft.description" rows="3" placeholder="이슈 내용" /></label>
        <label>우선순위<select v-model="draft.priority"><option value="HIGH">높음</option><option value="MEDIUM">보통</option><option value="LOW">낮음</option><option value="CRITICAL">긴급</option></select></label>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
        <button class="button button-primary" :disabled="saving" type="submit">{{ saving ? '등록 중…' : '등록' }}</button>
      </form>
      <div class="rail-filter">
        <label>이슈 상태 필터<select v-model="status"><option value="">전체 상태</option><option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label>이슈 검색<input v-model.trim="query" type="search" placeholder="이슈 검색" /></label>
      </div>
      <p class="rail-total">총 {{ filteredIssues.length }}개</p>
      <div class="rail-list">
        <NuxtLink v-for="issue in filteredIssues" :key="issue.id" :to="`/projects/${projectId}/issues/${issue.id}`">
          <small>E-{{ issue.issueNumber }}</small><h2>{{ issue.title }}</h2><p>{{ issue.description || '이슈 설명이 없습니다.' }}</p><footer><span>프로젝트: {{ project?.name }}</span><b>{{ statusLabel(issue.status) }}</b></footer>
        </NuxtLink>
        <p v-if="state === 'ready' && !filteredIssues.length" class="rail-empty">표시할 이슈가 없습니다.</p>
      </div>
    </aside>

    <main class="board-summary">
      <p v-if="state === 'loading'" class="screen-message" role="status" aria-live="polite">이슈를 불러오는 중입니다.</p>
      <div v-else-if="state === 'error'" class="screen-message" role="alert"><p>{{ error }}</p><button class="button button-primary" type="button" @click="loadProject">다시 시도</button></div>
      <template v-else>
        <p class="eyebrow">PROJECT OVERVIEW</p>
        <h1>{{ project?.name }}의 이슈를 관리하세요.</h1>
        <p class="summary-copy">카드를 선택하면 이슈 작업 공간에서 계획과 작업 현황을 확인할 수 있습니다.</p>
        <div class="project-stat-cards">
          <article><span>전체 이슈</span><strong>{{ issues.length }}</strong></article>
          <article><span>진행 중</span><strong>{{ inProgressCount }}</strong></article>
          <article><span>승인 대기</span><strong>{{ reviewCount }}</strong></article>
        </div>
      </template>
    </main>

    <aside class="board-preview" aria-label="선택 이슈 미리보기">
      <template v-if="selectedIssue">
        <p class="eyebrow">선택 이슈 미리보기</p><small>E-{{ selectedIssue.issueNumber }} · {{ statusLabel(selectedIssue.status) }}</small><h2>{{ selectedIssue.title }}</h2><p>{{ selectedIssue.description || '이슈 설명이 없습니다.' }}</p>
        <NuxtLink class="button button-primary" :to="`/projects/${projectId}/issues/${selectedIssue.id}`">작업 공간 열기</NuxtLink>
      </template>
      <p v-else>이슈를 등록하면 상세 작업을 시작할 수 있습니다.</p>
    </aside>
  </section>
</template>
