<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { isUnauthenticatedError, useControlPlaneApi } from '../composables/control-plane'

type Project = {
  id: string
  name: string
  repositoryUri: string | null
  baseBranch: string
}

type Issue = {
  id: string
  projectId: string
  issueNumber: number
  title: string
  description: string | null
  priority: string | null
  status: string
}

type ProjectWithIssues = Project & { issues: Issue[] }

const api = useControlPlaneApi()
const projects = ref<ProjectWithIssues[]>([])
const state = ref<'loading' | 'ready' | 'unauthenticated' | 'error'>('loading')
const error = ref('')
const projectQuery = ref('')
const issueQuery = ref('')
const showProjectForm = ref(false)
const saving = ref(false)
const formError = ref('')
const draft = reactive({ name: '', repositoryUri: '', baseBranch: 'main', credentialRef: '' })

const allIssues = computed(() => projects.value.flatMap((project) => project.issues))
const filteredProjects = computed(() => {
  const query = projectQuery.value.toLowerCase()
  return projects.value.filter((project) => !query || project.name.toLowerCase().includes(query))
})
const filteredIssues = computed(() => {
  const query = issueQuery.value.toLowerCase()
  return allIssues.value.filter((issue) => !query || `${issue.issueNumber} ${issue.title} ${issue.description ?? ''}`.toLowerCase().includes(query))
})
const activeIssues = computed(() => allIssues.value.filter((issue) => ['OPEN', 'IN_PROGRESS'].includes(issue.status)).length)
const doneIssues = computed(() => allIssues.value.filter((issue) => issue.status === 'DONE').length)

function statusLabel(status: string) {
  return { OPEN: '계획 중', IN_PROGRESS: '구현 중', IN_REVIEW: '승인 대기', DONE: '완료', FAILED: '재작업 필요' }[status] ?? status
}

async function loadProjects() {
  state.value = 'loading'
  error.value = ''
  try {
    const listed = await api.listProjects<Project[]>()
    projects.value = await Promise.all(listed.map(async (project) => ({
      ...project,
      issues: await api.listIssues<Issue[]>(project.id),
    })))
    state.value = 'ready'
  } catch (cause: unknown) {
    if (isUnauthenticatedError(cause)) {
      state.value = 'unauthenticated'
      return
    }
    error.value = cause instanceof Error ? cause.message : '프로젝트를 불러오지 못했습니다.'
    state.value = 'error'
  }
}

async function createProject() {
  saving.value = true
  formError.value = ''
  try {
    await api.createProject({
      name: draft.name,
      repositoryUri: draft.repositoryUri,
      baseBranch: draft.baseBranch,
      credentialRef: draft.credentialRef || undefined,
    })
    Object.assign(draft, { name: '', repositoryUri: '', baseBranch: 'main', credentialRef: '' })
    showProjectForm.value = false
    await loadProjects()
  } catch (cause: unknown) {
    if (isUnauthenticatedError(cause)) {
      state.value = 'unauthenticated'
      return
    }
    formError.value = '프로젝트를 등록하지 못했습니다. 입력값과 서버 연결을 확인하세요.'
  } finally {
    saving.value = false
  }
}

onMounted(loadProjects)
</script>

<template>
  <AuthGate v-if="state === 'unauthenticated'" @authenticated="loadProjects" />
  <section v-else class="dashboard" aria-labelledby="dashboard-title">
    <header class="dashboard-heading">
      <div>
        <h1 id="dashboard-title">Control Plane Dashboard</h1>
        <p>프로젝트와 이슈를 한곳에서 관리합니다.</p>
      </div>
      <label>
        프로젝트 검색
        <input v-model.trim="projectQuery" type="search" placeholder="프로젝트 검색" />
      </label>
    </header>

    <p v-if="state === 'loading'" class="screen-message" role="status" aria-live="polite">프로젝트를 불러오는 중입니다.</p>
    <div v-else-if="state === 'error'" class="screen-message" role="alert">
      <p>{{ error }}</p>
      <button class="button button-primary" type="button" @click="loadProjects">다시 시도</button>
    </div>
    <template v-else>
      <div class="metric-grid">
        <article><span>진행 중 이슈</span><strong>{{ activeIssues }}</strong></article>
        <article><span>완료된 이슈</span><strong>{{ doneIssues }}</strong></article>
        <article><span>등록된 프로젝트</span><strong>{{ projects.length }}</strong></article>
      </div>

      <section class="quick-actions" aria-label="프로젝트 작업">
        <button class="button button-primary" type="button" @click="showProjectForm = !showProjectForm">프로젝트 만들기</button>
      </section>
      <form v-if="showProjectForm" class="entry-form dashboard-form" @submit.prevent="createProject">
        <label>프로젝트 이름<input v-model.trim="draft.name" required maxlength="100" placeholder="예: 결제 플랫폼" /></label>
        <label>저장소 주소<input v-model.trim="draft.repositoryUri" required placeholder="https://github.com/team/repository.git" /></label>
        <label>기준 브랜치<input v-model.trim="draft.baseBranch" required placeholder="main" /></label>
        <label>인증 참조<input v-model.trim="draft.credentialRef" placeholder="선택 사항" /></label>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
        <button class="button button-primary" :disabled="saving" type="submit">{{ saving ? '등록 중…' : '프로젝트 등록' }}</button>
      </form>

      <section class="dashboard-projects" aria-labelledby="projects-title">
        <div>
          <h2 id="projects-title">등록된 프로젝트 <small>{{ filteredProjects.length }}</small></h2>
          <button class="text-button" type="button" @click="loadProjects">새로고침</button>
        </div>
        <p v-if="!filteredProjects.length">표시할 프로젝트가 없습니다.</p>
        <div v-else class="project-chips">
          <NuxtLink v-for="project in filteredProjects" :key="project.id" :to="`/projects/${project.id}`">
            <span>{{ project.name.slice(0, 1) || 'P' }}</span><b>{{ project.name }}</b><small>{{ project.issues.length }}개 이슈</small><i>›</i>
          </NuxtLink>
        </div>
      </section>

      <section class="dashboard-preview" aria-labelledby="issues-title">
        <aside>
          <h2 id="issues-title">이슈 목록</h2>
          <label>이슈 검색<input v-model.trim="issueQuery" type="search" placeholder="이슈 검색" /></label>
          <p>총 {{ filteredIssues.length }}개</p>
          <NuxtLink v-for="issue in filteredIssues.slice(0, 5)" :key="issue.id" :to="`/projects/${issue.projectId}/issues/${issue.id}`">
            <small>E-{{ issue.issueNumber }}</small><strong>{{ issue.title }}</strong><span>{{ statusLabel(issue.status) }}</span>
          </NuxtLink>
        </aside>
      </section>
    </template>
  </section>
</template>
