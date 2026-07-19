<template>
  <section class="dashboard" aria-labelledby="dashboard-title">
    <header class="dashboard-heading">
      <div>
        <h1 id="dashboard-title">홍길동 님, 안녕하세요!</h1>
        <p>오늘도 생산적인 하루 되세요.</p>
      </div>
      <select aria-label="프로젝트 필터">
        <option>전체 프로젝트</option>
        <option v-for="project in projects" :key="project.id">
          {{ project.name }}
        </option>
      </select>
    </header>
    <form
      v-if="showProjectForm"
      class="entry-form dashboard-form"
      @submit.prevent="createProject"
    >
      <label
        >프로젝트 이름<input
          v-model.trim="draft.name"
          required
          maxlength="100"
          placeholder="예: 결제 플랫폼" /></label
      ><label
        >저장소 주소<input
          v-model.trim="draft.repositoryUri"
          required
          placeholder="https://github.com/team/repository.git" /></label
      ><label
        >기준 브랜치<input
          v-model.trim="draft.baseBranch"
          required
          placeholder="main" /></label
      ><label
        >인증 참조<input
          v-model.trim="draft.credentialRef"
          placeholder="선택 사항"
      /></label>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <button class="button button-primary" :disabled="saving">
        {{ saving ? "등록 중…" : "프로젝트 등록" }}
      </button>
    </form>
    <div class="metric-grid">
      <article>
        <span>진행 중 이슈</span><strong>{{ activeIssues }}</strong
        ><small>지난 7일 대비 <b>↑ 8</b></small>
      </article>
      <article>
        <span>완료된 이슈</span><strong>{{ doneIssues }}</strong
        ><small>지난 7일 대비 <b>↑ 22</b></small>
      </article>
      <article>
        <span>진행 중 작업</span><strong>{{ runningJobs }}</strong
        ><small>지난 7일 대비 <b>↑ 3</b></small>
      </article>
      <article>
        <span>승인 대기</span><strong>{{ actionRequired }}</strong
        ><small>지난 7일 대비 <em>↓ 1</em></small>
      </article>
    </div>
    <section class="activity-feed">
      <h2>최근 활동</h2>
      <p v-for="(item, index) in feed" :key="index">
        <span :class="['feed-icon', item.type]">{{ item.icon }}</span
        >{{ item.text }}<time>{{ item.time }}</time>
      </p>
      <p v-if="!loading && !feed.length" class="feed-empty">
        새로운 프로젝트 또는 이슈를 등록해 작업을 시작하세요.
      </p>
    </section>
    <section class="quick-actions">
      <button
        type="button"
        class="action-card create"
        @click="showProjectForm = !showProjectForm"
      >
        <span>▧</span><strong>프로젝트 만들기</strong
        ><small>새로운 프로젝트를 시작합니다.</small></button
      ><button
        type="button"
        class="action-card connect"
        @click="showProjectForm = true"
      >
        <span>⌁</span><strong>저장소 연결하기</strong
        ><small>Git 저장소를 연결합니다.</small>
      </button>
    </section>
    <section class="dashboard-projects">
      <div>
        <h2>
          등록된 프로젝트 <small>{{ projects.length }}</small>
        </h2>
        <button class="text-button" @click="loadProjects">새로고침</button>
      </div>
      <p v-if="loading" class="screen-message">프로젝트를 불러오는 중입니다.</p>
      <p v-else-if="error" class="screen-message error-message">
        프로젝트를 불러오지 못했습니다.
      </p>
      <div v-else class="project-chips">
        <router-link
          v-for="project in projects"
          :key="project.id"
          :to="`/projects/${project.id}`"
          ><span>{{ project.name?.slice(0, 1) || "P" }}</span
          ><b>{{ project.name }}</b
          ><small>{{ project.issueCounts?.total || 0 }}개 이슈</small
          ><i>›</i></router-link
        >
      </div>
    </section>
    <section class="dashboard-preview">
      <aside>
        <h2>이슈 목록</h2>
        <label
          ><span>⌕</span
          ><input
            v-model.trim="issueQuery"
            type="search"
            placeholder="이슈 검색"
            aria-label="대시보드 이슈 검색"
        /></label>
        <p>총 {{ previewIssues.length }}개</p>
        <router-link
          v-for="issue in previewIssues.slice(0, 5)"
          :key="issue.id"
          :to="`/projects/${issue.projectId}/issues/${issue.id}`"
          ><small>E-{{ issue.issueNumber }}</small
          ><strong>{{ issue.title }}</strong
          ><span>{{ statusLabel(issue.status) }}</span></router-link
        >
      </aside>
      <article v-if="selectedIssue">
        <p class="eyebrow">선택 이슈</p>
        <small
          >E-{{ selectedIssue.issueNumber }} ·
          {{ statusLabel(selectedIssue.status) }}</small
        >
        <h2>{{ selectedIssue.title }}</h2>
        <p>{{ selectedIssue.description || "이슈 설명이 없습니다." }}</p>
        <div class="preview-plan">
          <strong>계획</strong
          ><span>작업 범위를 확인하고 AI 에이전트에 계획을 요청하세요.</span>
        </div>
        <div class="preview-plan">
          <strong>에이전트 팀 활동</strong
          ><span
            >계획 · 구현 · 테스트 · 승인 워크플로를 한곳에서 확인합니다.</span
          >
        </div>
        <router-link
          class="button button-primary"
          :to="`/projects/${selectedIssue.projectId}/issues/${selectedIssue.id}`"
          >이슈 작업 공간 열기</router-link
        >
      </article>
      <article v-else class="preview-empty">
        <h2>이슈를 선택하세요.</h2>
        <p>
          프로젝트에 이슈를 등록하면 계획과 AI 작업 현황을 미리 볼 수 있습니다.
        </p>
      </article>
    </section>
  </section>
</template>
<script>
import { ProjectApi } from "../api";
export default {
  data: () => ({
    projects: [],
    loading: true,
    error: false,
    issueQuery: "",
    showProjectForm: false,
    saving: false,
    formError: "",
    draft: {
      name: "",
      repositoryUri: "",
      baseBranch: "main",
      credentialRef: "",
    },
  }),
  computed: {
    allIssues() {
      return this.projects.flatMap((p) =>
        (p.issues || []).map((issue) => ({ ...issue, projectId: p.id })),
      );
    },
    previewIssues() {
      const q = this.issueQuery.toLowerCase();
      return this.allIssues.filter(
        (issue) =>
          !q ||
          `${issue.issueNumber} ${issue.title} ${issue.description || ""}`
            .toLowerCase()
            .includes(q),
      );
    },
    selectedIssue() {
      return this.previewIssues[0] || this.allIssues[0];
    },
    activeIssues() {
      return this.allIssues.filter((i) =>
        ["OPEN", "IN_PROGRESS"].includes(i.status),
      ).length;
    },
    doneIssues() {
      return this.allIssues.filter((i) => i.status === "DONE").length;
    },
    runningJobs() {
      return this.allIssues.filter((i) => i.status === "IN_PROGRESS").length;
    },
    actionRequired() {
      return this.allIssues.filter((i) =>
        ["IN_REVIEW", "FAILED"].includes(i.status),
      ).length;
    },
    feed() {
      return this.allIssues
        .slice(0, 5)
        .map((issue, index) => ({
          icon:
            issue.status === "FAILED"
              ? "×"
              : issue.status === "DONE"
                ? "✓"
                : "▣",
          type:
            issue.status === "FAILED"
              ? "failed"
              : issue.status === "DONE"
                ? "done"
                : "progress",
          text: `이슈 #E-${issue.issueNumber} “${issue.title}” ${issue.status === "DONE" ? "이 승인되었습니다." : issue.status === "FAILED" ? "이 재검토되었습니다." : "이 진행 중입니다."}`,
          time: `${index + 1}시간 전`,
        }));
    },
  },
  mounted() {
    this.loadProjects();
  },
  methods: {
    async loadProjects() {
      this.loading = true;
      this.error = false;
      try {
        const { data } = await ProjectApi.list();
        this.projects = await Promise.all(
          data.map(async (project) => {
            try {
              const { data: issues } = await ProjectApi.issues(project.id);
              return {
                ...project,
                issues,
                issueCounts: { total: issues.length },
              };
            } catch {
              return { ...project, issues: [], issueCounts: { total: 0 } };
            }
          }),
        );
      } catch {
        this.error = true;
      } finally {
        this.loading = false;
      }
    },
    async createProject() {
      this.saving = true;
      this.formError = "";
      try {
        await ProjectApi.create({
          ...this.draft,
          credentialRef: this.draft.credentialRef || null,
        });
        this.draft = {
          name: "",
          repositoryUri: "",
          baseBranch: "main",
          credentialRef: "",
        };
        this.showProjectForm = false;
        await this.loadProjects();
      } catch {
        this.formError =
          "프로젝트를 등록하지 못했습니다. 입력값과 서버 연결을 확인하세요.";
      } finally {
        this.saving = false;
      }
    },
    statusLabel(status) {
      return (
        {
          OPEN: "계획 중",
          IN_PROGRESS: "구현 중",
          IN_REVIEW: "승인 대기",
          DONE: "완료",
          FAILED: "재작업 필요",
        }[status] || "진행 중"
      );
    },
  },
};
</script>
