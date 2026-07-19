import { createRouter, createWebHashHistory } from 'vue-router'
import Dashboard from '../components/Dashboard.vue'
import ProjectBoard from '../components/ProjectBoard.vue'
import IssueWorkspace from '../components/IssueWorkspace.vue'

export default createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: Dashboard },
    { path: '/projects/:projectId', component: ProjectBoard },
    { path: '/projects/:projectId/issues/:issueId', component: IssueWorkspace },
  ],
})
