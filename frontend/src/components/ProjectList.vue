<template>
  <div>
    <div class="page-header">
      <span class="page-title">Projects</span>
      <router-link class="btn btn-primary" to="/projects/new">+ New Project</router-link>
    </div>
    <div v-if="loading" class="loading">불러오는 중...</div>
    <div v-else-if="projects.length === 0" class="empty-state">등록된 프로젝트가 없습니다.</div>
    <table v-else class="table">
      <thead>
        <tr>
          <th>이름</th>
          <th>로컬 경로</th>
          <th>기준 브랜치</th>
          <th>등록일</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in projects" :key="p.id" @click="$router.push('/projects/' + p.id)">
          <td><strong>{{ p.name }}</strong></td>
          <td style="font-family:monospace;font-size:12px">{{ p.localPath }}</td>
          <td><code>{{ p.baseBranch }}</code></td>
          <td>{{ formatDate(p.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import { ProjectApi } from '../api'

export default {
  data() {
    return { projects: [], loading: true }
  },
  async mounted() {
    try {
      const res = await ProjectApi.list()
      this.projects = res.data
    } finally {
      this.loading = false
    }
  },
  methods: {
    formatDate(dt) {
      return dt ? new Date(dt).toLocaleDateString('ko-KR') : '-'
    },
  },
}
</script>
