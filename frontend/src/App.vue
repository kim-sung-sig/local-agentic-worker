<template>
  <div id="app">
    <header class="header">
      <h1>Agentic Worker</h1>
      <div class="header-actions">
        <router-link class="btn btn-secondary btn-sm" to="/workflow-runs">Workflow Runs</router-link>
        <router-link class="btn btn-secondary btn-sm" to="/">Projects</router-link>
      </div>
    </header>
    <div class="layout">
      <nav class="sidebar">
        <div class="sidebar-title">Projects</div>
        <div v-for="p in projects" :key="p.id">
          <router-link
            class="sidebar-item"
            :class="{ active: $route.params.id === p.id }"
            :to="'/projects/' + p.id">
            {{ p.name }}
          </router-link>
        </div>
        <div v-if="projects.length === 0" style="padding:0 16px;color:#9ca3af;font-size:12px">없음</div>
      </nav>
      <main class="main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script>
import { ProjectApi } from './api'

export default {
  data() {
    return { projects: [] }
  },
  async mounted() {
    await this.loadProjects()
  },
  watch: {
    '$route'(to) {
      if (to.path === '/') this.loadProjects()
    },
  },
  methods: {
    async loadProjects() {
      try {
        const res = await ProjectApi.list()
        this.projects = res.data
      } catch (e) {
        console.error('프로젝트 목록 로드 실패', e)
      }
    },
  },
}
</script>
