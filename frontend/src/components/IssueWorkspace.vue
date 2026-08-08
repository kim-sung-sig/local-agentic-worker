<template>
  <section class="issue-workspace">
    <button
      class="issue-list-toggle"
      type="button"
      aria-controls="workspace-issue-list"
      :aria-expanded="String(drawerOpen)"
      @click="drawerOpen = true"
    >
      ☰ 이슈 목록
    </button>
    <button
      v-if="drawerOpen"
      class="issue-rail-backdrop"
      type="button"
      aria-label="이슈 목록 닫기"
      @click="drawerOpen = false"
    ></button>
    <aside
      id="workspace-issue-list"
      class="issue-rail workspace-rail"
      :class="{ 'is-open': drawerOpen }"
    >
      <header>
        <div class="rail-heading"><h2>이슈 목록</h2><button type="button" class="rail-close" @click="drawerOpen = false">닫기</button></div>
        <select>
          <option>전체 상태</option></select
        ><label
          ><span>⌕</span
          ><input v-model.trim="query" type="search" placeholder="이슈 검색"
        /></label>
      </header>
      <p class="rail-total">총 {{ visibleIssues.length }}개</p>
      <div class="rail-list">
        <router-link
          v-for="item in visibleIssues"
          :key="item.id"
          :class="{ active: item.id === issue.id }"
          :to="`/projects/${$route.params.projectId}/issues/${item.id}`"
          @click="drawerOpen = false"
          ><small>E-{{ item.issueNumber }}</small>
          <h2>{{ item.title }}</h2>
          <p>{{ item.description || "이슈 설명이 없습니다." }}</p>
          <footer>
            <span>프로젝트: {{ project?.name }}</span
            ><b :data-status="item.status">{{ statusLabel(item.status) }}</b>
          </footer></router-link
        >
      </div>
    </aside>
    <div v-if="loading" class="screen-message">이슈를 불러오는 중입니다.</div>
    <div v-else-if="error" class="screen-message error-message">
      이슈를 불러오지 못했습니다.
    </div>
    <template v-else
      ><main class="issue-detail">
        <header class="detail-header">
          <p>
            <router-link :to="`/projects/${$route.params.projectId}`"
              >이슈</router-link
            >
            <span>/</span> E-{{ issue.issueNumber }}
          </p>
          <h1 id="issue-title">{{ issue.title }}</h1>
          <div class="issue-meta">
            <span
              >상태: <b>{{ statusLabel(issue.status) }}</b></span
            ><span
              >우선순위: <em>{{ priorityLabel(issue.priority) }}</em></span
            ><span>담당자: 홍길동</span><span>생성일: {{ createdDate }}</span
            ><button type="button">•••</button
            ><button type="button" class="edit-button">이슈 편집</button>
            <button ref="executionDrawerTrigger" type="button" class="edit-button" @click="openExecutionDrawer">실행 현황</button>
          </div>
          <nav class="detail-tabs">
            <a class="selected">개요</a><a>작업</a><a>코멘트 <small>3</small></a
            ><a>파일 <small>2</small></a
            ><a>실행 기록</a>
          </nav>
        </header>
        <section class="detail-card description-card">
          <h2>설명</h2>
          <p>{{ issue.description || "이슈 설명이 없습니다." }}</p>
          <ul>
            <li>현재 요구사항과 영향 범위를 확인합니다.</li>
            <li>검토 가능한 완료 기준을 정의합니다.</li>
            <li>관련 API와 화면 변경사항을 정리합니다.</li>
          </ul>
        </section>
        <section class="detail-card editor-card">
          <header>
            <div>
              <h2>계획 <small>초안</small></h2>
              <p>AI 팀에 전달할 작업 범위를 계속 수정할 수 있습니다.</p>
            </div>
            <button class="edit-button" @click="run('plan')">✎ AI 요청</button>
          </header>
          <textarea
            v-model="plan"
            rows="11"
            aria-label="작업 계획 편집"
          ></textarea>
          <footer>
            <span>{{ planState }}</span
            ><button class="text-button" @click="save('plan')">저장</button>
          </footer>
        </section>
        <section class="detail-card editor-card">
          <header>
            <div>
              <h2>구현 계획 <small class="blue">작업 중</small></h2>
              <p>계획을 바탕으로 구현과 검증 순서를 관리합니다.</p>
            </div>
            <button
              class="edit-button"
              :disabled="!planReady"
              @click="run('design')"
            >
              ✎ AI 요청
            </button>
          </header>
          <p v-if="!planReady" class="phase-hint">
            계획 에이전트가 완료되면 구현계획을 요청할 수 있습니다.
          </p>
          <textarea
            v-model="design"
            rows="11"
            aria-label="구현 계획 편집"
          ></textarea>
          <footer>
            <span>{{ designState }}</span
            ><button class="text-button" @click="save('design')">저장</button>
          </footer>
        </section>
      </main>
      <button v-if="executionDrawerOpen" class="execution-drawer-backdrop" type="button" aria-label="실행 현황 닫기" @click="closeExecutionDrawer"></button>
      <aside v-if="executionDrawerOpen" class="activity-panel execution-drawer" role="dialog" aria-modal="true" aria-label="실행 현황">
        <header>
          <div>
            <h2>에이전트 팀 활동</h2>
            <small class="live-dot">● 현재 단계</small>
          </div>
          <button type="button" class="rail-close" @click="closeExecutionDrawer">닫기</button>
          <button
            class="button button-primary full-button"
            @click="run('develop')"
          >
            구현 시작
          </button>
        </header>
        <ol class="activity-timeline">
          <li v-for="item in activity" :key="item.label">
            <span :class="item.state"></span>
            <div>
              <strong>{{ item.label }}</strong
              ><small>{{
                item.state === "done"
                  ? "완료"
                  : item.state === "working"
                    ? "작업 중"
                    : "대기"
              }}</small>
              <p>{{ item.detail }}</p>
              <a v-if="item.state === 'done'">{{ item.label }} 결과 보기</a>
            </div>
          </li>
        </ol>
        <div class="approval-box">
          <h3>승인 대기</h3>
          <small>홍길동 · 1분 전</small>
          <p>구현 계획에 대한 승인이 필요합니다.</p>
          <div>
            <button class="button button-primary" @click="decide('승인')">
              승인</button
            ><button class="button button-danger" @click="decide('반려')">
              거절
            </button>
          </div>
          <small v-if="decisionMessage">{{ decisionMessage }}</small
          ><small>Mock: 서버 워크플로 연결 전 화면 상태만 변경합니다.</small>
        </div>
      </aside></template
    >
    <p v-if="actionMessage" class="toast" role="status">{{ actionMessage }}</p>
  </section>
</template>
<script>
import { AgentApi, IssueApi, ProjectApi } from "../api";
import { shouldCloseExecutionDrawer } from "../lib/operator-console";
const fallbackPlan = `## 목표\n\n문제를 해결할 범위와 완료 기준을 작성하세요.\n\n## 세부 계획\n\n1. 현재 동작과 영향 범위를 확인합니다.\n2. 변경 방향과 검증 방법을 정리합니다.\n3. 검토 후 구현 계획으로 넘깁니다.`;
const fallbackDesign = `## 기술 변경 사항\n\n- 화면과 API 영향 범위를 정리합니다.\n- 검증 가능한 완료 기준을 추가합니다.\n\n## 영향 범위\n\n- 관련 화면과 API\n- 테스트 및 운영 확인 사항`;
const labels = {
  OPEN: "계획 중",
  IN_PROGRESS: "구현 중",
  IN_REVIEW: "승인 대기",
  DONE: "완료",
  FAILED: "재작업 필요",
};
export default {
  data: () => ({
    issue: {},
    project: null,
    issues: [],
    query: "",
    loading: true,
    error: false,
    plan: fallbackPlan,
    design: fallbackDesign,
    planState: "초안 · 이 브라우저에 저장되지 않음",
    designState: "초안 · 이 브라우저에 저장되지 않음",
    actionMessage: "",
    decisionMessage: "",
    drawerOpen: false,
    executionDrawerOpen: false,
    activity: [
      {
        label: "코드 구현",
        detail: "코드 에이전트가 작업을 기다리고 있습니다.",
        state: "waiting",
      },
      {
        label: "테스트 수행",
        detail: "테스트 에이전트가 대기 중입니다.",
        state: "waiting",
      },
      {
        label: "보안 검토",
        detail: "보안 에이전트가 대기 중입니다.",
        state: "waiting",
      },
      {
        label: "계획 수립",
        detail: "기획 에이전트가 계획을 확인합니다.",
        state: "waiting",
      },
    ],
  }),
  computed: {
    planReady() {
      return this.activity.some(
        (item) => item.label === "계획 수립" && item.state === "done",
      );
    },
    visibleIssues() {
      const q = this.query.toLowerCase();
      return this.issues.filter(
        (item) =>
          !q ||
          `${item.issueNumber} ${item.title} ${item.description || ""}`
            .toLowerCase()
            .includes(q),
      );
    },
    createdDate() {
      return this.issue.createdAt
        ? String(this.issue.createdAt).slice(0, 10)
        : "2024-05-21";
    },
  },
  mounted() {
    this.loadIssue();
    window.addEventListener("keydown", this.handleKeydown);
  },
  beforeUnmount() {
    window.removeEventListener("keydown", this.handleKeydown);
  },
  watch: { "$route.params.issueId": "loadIssue" },
  methods: {
    openExecutionDrawer() {
      this.executionDrawerOpen = true;
    },
    closeExecutionDrawer() {
      if (!this.executionDrawerOpen) return;
      this.executionDrawerOpen = false;
      this.$nextTick(() => this.$refs.executionDrawerTrigger?.focus());
    },
    handleKeydown(event) {
      if (shouldCloseExecutionDrawer(event.key)) this.closeExecutionDrawer();
    },
    async loadIssue() {
      this.loading = true;
      this.error = false;
      try {
        const id = this.$route.params.issueId;
        const projectId = this.$route.params.projectId;
        const [issue, project, issues] = await Promise.all([
          IssueApi.get(id),
          ProjectApi.get(projectId),
          ProjectApi.issues(projectId),
        ]);
        this.issue = issue.data;
        this.project = project.data;
        this.issues = issues.data;
        await this.loadDocuments();
        this.loadDrafts();
      } catch {
        this.error = true;
      } finally {
        this.loading = false;
      }
    },
    async loadDocuments() {
      const [plan, design, phases] = await Promise.allSettled([
        AgentApi.document(this.issue.id, "plan"),
        AgentApi.document(this.issue.id, "design"),
        AgentApi.phases(this.issue.id),
      ]);
      if (plan.status === "fulfilled") {
        this.plan = plan.value.data.content;
        this.planState = "AI 계획 문서 불러옴";
      }
      if (design.status === "fulfilled") {
        this.design = design.value.data.content;
        this.designState = "AI 구현계획 문서 불러옴";
      }
      if (phases.status === "fulfilled")
        this.activity = this.activity.map((item) => {
          const phase = { "계획 수립": "PLAN", "코드 구현": "DEVELOP" }[
            item.label
          ];
          const job = phases.value.data.find((value) => value.phase === phase);
          return job
            ? {
                ...item,
                detail: this.jobStatusLabel(job.status),
                state: job.status === "SUCCEEDED" ? "done" : "working",
              }
            : item;
        });
    },
    async run(phase) {
      const label = { plan: "계획", design: "구현계획", develop: "구현" }[
        phase
      ];
      try {
        await AgentApi.start(this.issue.id, phase);
        this.actionMessage = `${label} 에이전트에 요청했습니다.`;
      } catch {
        this.actionMessage = `${label} 요청은 Mock 상태로 표시됩니다. 백엔드 연결 후 실제 실행 결과가 반영됩니다.`;
      }
      const item = this.activity.find(
        (value) =>
          value.label ===
          (phase === "plan"
            ? "계획 수립"
            : phase === "develop"
              ? "코드 구현"
              : "테스트 수행"),
      );
      if (item) {
        item.state = "working";
        item.detail = "작업을 시작했습니다.";
      }
    },
    statusLabel(status) {
      return (
        {
          OPEN: "대기",
          PLAN_IN_PROGRESS: "계획 진행 중",
          DESIGN_IN_PROGRESS: "구현계획 진행 중",
          DEVELOP_IN_PROGRESS: "구현 진행 중",
          IN_PROGRESS: "진행 중",
          IN_REVIEW: "승인 대기",
          DONE: "완료",
          FAILED: "실패",
        }[status] || "상태 확인 중"
      );
    },
    jobStatusLabel(status) {
      return ({
        SUCCEEDED: "작업이 완료되었습니다.",
        PLAN_IN_PROGRESS: "계획을 수립하고 있습니다.",
        DESIGN_IN_PROGRESS: "구현 계획을 작성하고 있습니다.",
        DEVELOP_IN_PROGRESS: "코드를 구현하고 있습니다.",
        IN_PROGRESS: "작업을 진행하고 있습니다.",
        FAILED: "작업에 실패해 재시도가 필요합니다.",
      }[status] || "작업 상태를 확인하고 있습니다.");
    },
    loadDrafts() {
      for (const type of ["plan", "design"]) {
        const draft = localStorage.getItem(this.draftKey(type));
        if (draft != null) {
          this[type] = draft;
          this[`${type}State`] =
            `저장된 ${type === "plan" ? "계획" : "구현계획"} 초안`;
        }
      }
    },
    draftKey(type) {
      return `agentic-worker:issue:${this.issue.id}:${type}`;
    },
    save(type) {
      localStorage.setItem(this.draftKey(type), this[type]);
      this[`${type}State`] =
        `${type === "plan" ? "계획" : "구현계획"} 초안을 이 브라우저에 저장했습니다.`;
    },
    decide(decision) {
      this.decisionMessage = `Mock ${decision}: 서버에는 전달하지 않았습니다.`;
    },
    statusLabel: (s) => labels[s] || s || "계획 중",
    priorityLabel: (p) =>
      ({ CRITICAL: "긴급", HIGH: "높음", MEDIUM: "보통", LOW: "낮음" })[p] ||
      "보통",
  },
};
</script>
