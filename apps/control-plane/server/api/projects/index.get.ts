import { defineEventHandler } from 'h3'
import { listProjects } from '../../utils/project-service.js'

export default defineEventHandler(() => listProjects())
