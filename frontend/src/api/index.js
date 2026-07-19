import axios from 'axios'

const http = axios.create({ baseURL: '/api' })

export const ProjectApi = {
  list: () => http.get('/projects'),
  get: (projectId) => http.get(`/projects/${projectId}`),
  create: (payload) => http.post('/projects', payload),
  issues: (projectId) => http.get(`/projects/${projectId}/issues`),
  createIssue: (projectId, payload) => http.post(`/projects/${projectId}/issues`, payload),
}

export const IssueApi = {
  get: (issueId) => http.get(`/issues/${issueId}`),
}

export const AgentApi = {
  start: (issueId, phase) => http.post(`/issues/${issueId}/agent/${phase}`),
  phases: (issueId) => http.get(`/issues/${issueId}/agent/phases`),
  document: (issueId, type) => http.get(`/issues/${issueId}/agent/documents/${type}`),
}

export const ProjectNotificationApi = {
  list: (projectId, params) => http.get(`/projects/${projectId}/notifications`, { params }),
  unreadCount: (projectId) => http.get(`/projects/${projectId}/notifications/unread-count`),
}
