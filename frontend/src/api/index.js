import axios from 'axios'

const http = axios.create({ baseURL: '/api' })

export const ProjectApi = {
  list:   ()        => http.get('/projects'),
  get:    (id)      => http.get(`/projects/${id}`),
  create: (payload) => http.post('/projects', payload),
}

export const IssueApi = {
  list:         (projectId)          => http.get(`/projects/${projectId}/issues`),
  get:          (id)                 => http.get(`/issues/${id}`),
  create:       (projectId, payload) => http.post(`/projects/${projectId}/issues`, payload),
  updateStatus: (id, status)         => http.patch(`/issues/${id}/status`, { status }),
  getAgentJob:  (id)                 => http.get(`/issues/${id}/agent-job`),
  review:       (id, payload)        => http.post(`/issues/${id}/review`, payload),
}

export const AgentPhaseApi = {
  /** 페이즈 트리거 */
  startPlan:       (issueId) => http.post(`/issues/${issueId}/agent/plan`),
  startDesign:     (issueId) => http.post(`/issues/${issueId}/agent/design`),
  startDevelop:    (issueId) => http.post(`/issues/${issueId}/agent/develop`),
  startPlanDesign: (issueId) => http.post(`/issues/${issueId}/agent/plan-design`),

  /** 페이즈별 상태 목록 */
  getPhases: (issueId) => http.get(`/issues/${issueId}/agent/phases`),

  /** 산출물 문서 조회 */
  getPlanDocument:   (issueId) => http.get(`/issues/${issueId}/agent/documents/plan`),
  getDesignDocument: (issueId) => http.get(`/issues/${issueId}/agent/documents/design`),
}
