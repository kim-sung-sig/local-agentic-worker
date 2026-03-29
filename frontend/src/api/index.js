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
