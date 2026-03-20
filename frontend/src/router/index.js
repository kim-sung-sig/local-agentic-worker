import { createRouter, createWebHashHistory } from 'vue-router'
import ProjectList from '../components/ProjectList.vue'
import ProjectForm from '../components/ProjectForm.vue'
import IssueList from '../components/IssueList.vue'
import IssueForm from '../components/IssueForm.vue'
import IssueDetail from '../components/IssueDetail.vue'

const routes = [
  { path: '/',                                component: ProjectList },
  { path: '/projects/new',                    component: ProjectForm },
  { path: '/projects/:id',                    component: IssueList },
  { path: '/projects/:projectId/issues/new',  component: IssueForm },
  { path: '/issues/:id',                      component: IssueDetail },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
