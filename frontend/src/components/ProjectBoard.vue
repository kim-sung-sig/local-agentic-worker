<template>
  <section class="project-board" aria-labelledby="project-title">
    <aside class="issue-rail board-rail">
      <header>
        <p>프로젝트</p>
        <h1 id="project-title">{{ project?.name || "프로젝트" }}</h1>
        <small>{{ project?.repositoryUri || "저장소를 확인 중입니다." }}</small>
      </header>
      <button class="rail-add" @click="showIssueForm = !showIssueForm">
        ＋ 이슈 등록
      </button>
      <form
        v-if="showIssueForm"
        class="rail-form"
        @submit.prevent="createIssue"
      >
        <label class="sr-only"
          >이슈 제목<input
            v-model.trim="draft.title"
            required
            placeholder="이슈 제목" /></label
        ><label class="sr-only"
          >이슈 내용<textarea
            v-model.trim="draft.description"
            rows="3"
            placeholder="이슈 내용"
          ></textarea></label
        ><label class="sr-only"
          >우선순위<select v-model="draft.priority">
            <option value="HIGH">높음</option>
            <option value="MEDIUM">보통</option>
            <option value="LOW">낮음</option>
            <option value="CRITICAL">긴급</option>
          </select></label
        ><button class="button button-primary" :disabled="saving">
          {{ saving ? "등록 중…" : "등록" }}
        </button>
        <p v-if="formError" class="form-error">{{ formError }}</p>
      </form>
      <div class="rail-filter">
        <select v-model="status" aria-label="이슈 상태 필터">
          <option value="">전체 상태</option>
          <option
            v-for="item in statusOptions"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </option></select
        ><label
          ><span>⌕</span
          ><input
            v-model.trim="query"
            type="search"
            placeholder="이슈 검색"
            aria-label="이슈 검색"
        /></label>
      </div>
      <p class="rail-total">총 {{ filteredIssues.length }}개</p>
      <div class="rail-list">
        <router-link
          v-for="issue in filteredIssues"
          :key="issue.id"
          :to="`/projects/${$route.params.projectId}/issues/${issue.id}`"
          ><small>E-{{ issue.issueNumber }}</small>
          <h2>{{ issue.title }}</h2>
          <p>{{ issue.description || "이슈 설명이 없습니다." }}</p>
          <footer>
            <span>프로젝트: {{ project?.name }}</span
            ><b :data-status="issue.status">{{ statusLabel(issue.status) }}</b>
          </footer></router-link
        >
        <p v-if="!loading && !filteredIssues.length" class="rail-empty">
          표시할 이슈가 없습니다.
        </p>
      </div>
    </aside>
    <main class="board-summary">
      <div v-if="loading" class="screen-message">이슈를 불러오는 중입니다.</div>
      <div v-else-if="error" class="screen-message error-message">
        이슈를 불러오지 못했습니다.
      </div>
      <template v-else
        ><p class="eyebrow">PROJECT OVERVIEW</p>
        <h1>{{ project?.name }}의 이슈를 관리하세요.</h1>
        <p class="summary-copy">
          카드를 선택하면 계획, 구현계획, AI 에이전트 작업 현황을 한 작업
          공간에서 확인할 수 있습니다.
        </p>
        <div class="project-stat-cards">
          <article>
            <span>전체 이슈</span><strong>{{ issues.length }}</strong>
          </article>
          <article>
            <span>진행 중</span
            ><strong>{{
              issues.filter((i) => i.status === "IN_PROGRESS").length
            }}</strong>
          </article>
          <article>
            <span>승인 대기</span
            ><strong>{{
              issues.filter((i) => i.status === "IN_REVIEW").length
            }}</strong>
          </article>
        </div>
        <div class="board-callout">
          <span>✦</span>
          <div>
            <h2>이슈를 선택해 AI 작업을 시작하세요.</h2>
            <p>계획 수립부터 구현, 검토와 승인까지 이슈별로 연결됩니다.</p>
          </div>
        </div></template
      >
    </main>
    <aside class="board-preview">
      <p class="eyebrow">선택 이슈 미리보기</p>
      <template v-if="selectedIssue"
        ><small
          >E-{{ selectedIssue.issueNumber }} ·
          {{ statusLabel(selectedIssue.status) }}</small
        >
        <h2>{{ selectedIssue.title }}</h2>
        <p>{{ selectedIssue.description || "이슈 설명이 없습니다." }}</p>
        <div>
          <strong>다음 단계</strong
          ><span>계획을 작성하고 AI 에이전트에 요청하세요.</span>
        </div>
        <router-link
          class="button button-primary"
          :to="`/projects/${$route.params.projectId}/issues/${selectedIssue.id}`"
          >작업 공간 열기</router-link
        ></template
      >
      <p v-else>이슈를 등록하면 상세 작업을 시작할 수 있습니다.</p>
    </aside>
  </section>
</template>
<script>
import { ProjectApi } from "../api";
const statuses = {
  OPEN: "계획 전",
  IN_PROGRESS: "구현 중",
  IN_REVIEW: "승인 대기",
  DONE: "완료",
  FAILED: "재작업 필요",
};
export default {
  data: () => ({
    project: null,
    issues: [],
    loading: true,
    error: false,
    query: "",
    status: "",
    showIssueForm: false,
    saving: false,
    formError: "",
    draft: { title: "", description: "", priority: "MEDIUM" },
    statusOptions: Object.entries(statuses).map(([value, label]) => ({
      value,
      label,
    })),
  }),
  computed: {
    filteredIssues() {
      const q = this.query.toLowerCase();
      return this.issues.filter(
        (i) =>
          (!this.status || i.status === this.status) &&
          (!q ||
            `${i.issueNumber} ${i.title} ${i.description || ""}`
              .toLowerCase()
              .includes(q)),
      );
    },
    selectedIssue() {
      return this.filteredIssues[0] || this.issues[0];
    },
  },
  mounted() {
    this.loadProject();
  },
  watch: { "$route.params.projectId": "loadProject" },
  methods: {
    async loadProject() {
      this.loading = true;
      this.error = false;
      try {
        const id = this.$route.params.projectId;
        const [project, issues] = await Promise.all([
          ProjectApi.get(id),
          ProjectApi.issues(id),
        ]);
        this.project = project.data;
        this.issues = issues.data;
      } catch {
        this.error = true;
      } finally {
        this.loading = false;
      }
    },
    async createIssue() {
      this.saving = true;
      this.formError = "";
      try {
        await ProjectApi.createIssue(this.$route.params.projectId, this.draft);
        this.draft = { title: "", description: "", priority: "MEDIUM" };
        this.showIssueForm = false;
        await this.loadProject();
      } catch {
        this.formError = "이슈를 등록하지 못했습니다. 서버 연결을 확인하세요.";
      } finally {
        this.saving = false;
      }
    },
    statusLabel: (s) => statuses[s] || s || "계획 전",
  },
};
</script>
