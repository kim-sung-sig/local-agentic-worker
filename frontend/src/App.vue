<template>
  <div class="app-shell">
    <button class="navigation-toggle" type="button" aria-controls="primary-navigation" :aria-expanded="String(navigationOpen)" aria-label="메뉴 열기" @click="navigationOpen = !navigationOpen">☰</button>
    <button v-if="navigationOpen" class="navigation-backdrop" type="button" aria-label="메뉴 닫기" @click="navigationOpen = false"></button>
    <aside id="primary-navigation" class="side-nav" :class="{ 'is-open': navigationOpen }">
      <router-link class="brand" to="/"
        ><span class="brand-mark">◆</span
        ><span>AI 개발 워크벤치</span></router-link
      >
      <nav class="primary-nav" aria-label="주요 메뉴">
        <router-link to="/" exact @click="closeNavigation">⌂ <span>대시보드</span></router-link>
        <router-link :to="projectLink" exact @click="closeNavigation"
          >▤ <span>프로젝트</span></router-link
        >
        <router-link v-if="issueLink" :to="issueLink" exact @click="closeNavigation"
          >◉ <span>이슈</span></router-link
        >
        <button v-else type="button" disabled>◉ <span>이슈</span></button>
        <button type="button" disabled>▱ <span>저장소</span></button>
        <button type="button" disabled>✦ <span>에이전트</span></button>
        <button type="button" disabled>◷ <span>실행 기록</span></button>
      </nav>
      <div class="nav-divider"></div>
      <nav class="secondary-nav" aria-label="보조 메뉴">
        <a href="#">▤ <span>템플릿</span></a
        ><a href="#">⚙ <span>설정</span></a>
      </nav>
      <div class="recent-projects">
        <div>
          <span>최근 프로젝트</span
          ><button type="button" title="프로젝트 추가">＋</button>
        </div>
        <router-link to="/">● AI 챗봇 플랫폼</router-link
        ><router-link to="/">● 데이터 파이프라인</router-link
        ><router-link to="/">● 사내 검색 시스템</router-link
        ><router-link to="/">● 이미지 분석 서비스</router-link>
      </div>
      <div class="user-card">
        <span>HJ</span>
        <div><strong>홍길동</strong><small>hong@example.com</small></div>
        <b>›</b>
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <strong>{{ pageTitle }}</strong>
        <div class="top-actions">
          <label class="global-search"
            ><span>⌕</span
            ><input type="search" placeholder="검색 (⌘K)" /></label
          ><button type="button" aria-label="도움말">▱</button
          ><button type="button" aria-label="알림">♧</button
          ><span class="top-avatar">HJ</span><span>⌄</span>
        </div>
      </header>
      <main class="app-content"><router-view /></main>
    </div>
  </div>
</template>

<script>
export default {
  data: () => ({ navigationOpen: false }),
  computed: {
    pageTitle() {
      return this.$route.path === "/"
        ? "대시보드"
        : this.issueLink
          ? "이슈 작업 공간"
          : "프로젝트";
    },
    projectLink() {
      const id = this.$route.params.projectId;
      return id ? `/projects/${id}` : "/";
    },
    issueLink() {
      const { projectId, issueId } = this.$route.params;
      return projectId && issueId
        ? `/projects/${projectId}/issues/${issueId}`
        : null;
    },
  },
  methods: {
    closeNavigation() {
      this.navigationOpen = false;
    },
  },
};
</script>
