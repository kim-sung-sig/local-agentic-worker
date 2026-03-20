<template>
  <div>
    <div class="page-header">
      <span class="page-title">{{ projectName }}</span>
      <router-link class="btn btn-primary" :to="'/projects/' + projectId + '/issues/new'">
        + New Issue
      </router-link>
    </div>
    <div v-if="loading" class="loading">불러오는 중...</div>
    <div v-else-if="issues.length === 0" class="empty-state">이슈가 없습니다.</div>
    <table v-else class="table">
      <thead>
        <tr>
          <th>#</th>
          <th>제목</th>
          <th>우선순위</th>
          <th>상태</th>
          <th>등록일</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="issue in issues" :key="issue.id" @click="$router.push('/issues/' + issue.id)">
          <td style="color:#9ca3af">{{ issue.issueNumber }}</td>
          <td>{{ issue.title }}</td>
          <td><span :class="priorityClass(issue.priority)" class="badge">{{ issue.priority }}</span></td>
          <td><span :class="statusClass(issue.status)" class="badge">{{ issue.status }}</span></td>
          <td>{{ formatDate(issue.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import { ProjectApi, IssueApi } from '../api'

export default {
  data() {
    return { issues: [], projectName: '', loading: true }
  },
  computed: {
    projectId() { return this.$route.params.id },
  },
  async mounted() {
    try {
      const [issueRes, projRes] = await Promise.all([
        IssueApi.list(this.projectId),
        ProjectApi.get(this.projectId),
      ])
      this.issues = issueRes.data
      this.projectName = projRes.data.name
    } finally {
      this.loading = false
    }
  },
  methods: {
    formatDate(dt) { return dt ? new Date(dt).toLocaleDateString('ko-KR') : '-' },
    statusClass(s) {
      return { OPEN: 'badge-open', IN_PROGRESS: 'badge-progress',
               IN_REVIEW: 'badge-review', FAILED: 'badge-failed', CLOSED: 'badge-closed' }[s] || ''
    },
    priorityClass(p) {
      return { LOW: 'badge-low', MEDIUM: 'badge-medium',
               HIGH: 'badge-high', CRITICAL: 'badge-critical' }[p] || ''
    },
  },
}
</script>
