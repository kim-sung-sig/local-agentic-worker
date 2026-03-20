<template>
  <div>
    <div class="page-header">
      <span class="page-title">New Project</span>
    </div>
    <div class="card">
      <div class="form-group">
        <label>이름</label>
        <input v-model="form.name" placeholder="프로젝트 이름" />
      </div>
      <div class="form-group">
        <label>로컬 경로</label>
        <input v-model="form.localPath" placeholder="/path/to/git/repo" />
      </div>
      <div class="form-group">
        <label>기준 브랜치</label>
        <input v-model="form.baseBranch" placeholder="main" />
      </div>
      <p v-if="error" class="error-msg">{{ error }}</p>
      <div class="form-actions">
        <button class="btn btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '생성 중...' : '프로젝트 등록' }}
        </button>
        <button class="btn btn-secondary" @click="$router.back()">취소</button>
      </div>
    </div>
  </div>
</template>

<script>
import { ProjectApi } from '../api'

export default {
  data() {
    return {
      form: { name: '', localPath: '', baseBranch: 'main' },
      submitting: false,
      error: null,
    }
  },
  methods: {
    async submit() {
      this.error = null
      if (!this.form.name) { this.error = '이름은 필수입니다.'; return }
      if (!this.form.localPath) { this.error = '로컬 경로는 필수입니다.'; return }
      this.submitting = true
      try {
        await ProjectApi.create(this.form)
        this.$router.push('/')
      } catch (e) {
        this.error = e.response?.data?.message || '프로젝트 등록 중 오류가 발생했습니다.'
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>
