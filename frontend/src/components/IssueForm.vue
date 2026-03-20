<template>
  <div>
    <div class="page-header">
      <span class="page-title">New Issue</span>
    </div>
    <div class="card">
      <div class="form-group">
        <label>제목</label>
        <input v-model="form.title" placeholder="이슈 제목을 입력하세요" />
      </div>
      <div class="form-group">
        <label>설명</label>
        <textarea v-model="form.description" placeholder="이슈 상세 내용..." rows="5"></textarea>
      </div>
      <div class="form-group">
        <label>우선순위</label>
        <select v-model="form.priority">
          <option value="LOW">LOW</option>
          <option value="MEDIUM">MEDIUM</option>
          <option value="HIGH">HIGH</option>
          <option value="CRITICAL">CRITICAL</option>
        </select>
      </div>
      <p v-if="error" class="error-msg">{{ error }}</p>
      <div class="form-actions">
        <button class="btn btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '생성 중...' : '이슈 생성' }}
        </button>
        <button class="btn btn-secondary" @click="$router.back()">취소</button>
      </div>
    </div>
  </div>
</template>

<script>
import { IssueApi } from '../api'

export default {
  data() {
    return {
      form: { title: '', description: '', priority: 'MEDIUM' },
      submitting: false,
      error: null,
    }
  },
  methods: {
    async submit() {
      this.error = null
      if (!this.form.title) { this.error = '제목은 필수입니다.'; return }
      this.submitting = true
      try {
        const projectId = this.$route.params.projectId
        await IssueApi.create(projectId, this.form)
        this.$router.push('/projects/' + projectId)
      } catch (e) {
        this.error = e.response?.data?.message || '이슈 생성 중 오류가 발생했습니다.'
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>
