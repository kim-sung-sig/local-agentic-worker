<template>
  <div class="notification-inbox">
    <button
      class="inbox-toggle"
      type="button"
      :aria-expanded="open"
      aria-controls="project-notification-inbox"
      @click="open = !open"
    >
      알림
      <span v-if="unreadCount" class="inbox-badge" :aria-label="`읽지 않은 알림 ${unreadCount}개`">{{ unreadCount }}</span>
    </button>
    <section v-if="open" id="project-notification-inbox" class="inbox-panel" aria-label="프로젝트 알림">
      <header class="inbox-heading">
        <h2>알림</h2>
        <span>{{ unreadCount }}개 읽지 않음</span>
      </header>
      <p v-if="connectionStatus" class="inbox-status" role="status">{{ connectionStatus }}</p>
      <p v-if="!notifications.length" class="inbox-empty">새 알림이 없습니다.</p>
      <ol v-else class="inbox-list">
        <li v-for="notification in notifications" :key="notification.eventId || notification.notificationId" :class="{ unread: !notification.readAt }">
          <strong>{{ notification.title }}</strong>
          <p>{{ notification.message }}</p>
          <time :datetime="notification.createdAt">{{ formatTime(notification.createdAt) }}</time>
        </li>
      </ol>
    </section>
  </div>
</template>

<script>
import { ProjectNotificationApi } from '../api'
import { mergeNotification } from '../lib/operator-console'

export default {
  props: { projectId: { type: [String, Number], required: true } },
  data: () => ({ notifications: [], unreadCount: 0, open: false, source: null, connectionStatus: '', inboxRequestId: 0 }),
  watch: {
    projectId() {
      this.source?.close()
      this.inboxRequestId += 1
      this.notifications = []
      this.unreadCount = 0
      this.connectionStatus = ''
      this.loadInbox()
      this.connect()
    },
  },
  mounted() {
    this.loadInbox()
    this.connect()
  },
  beforeUnmount() {
    this.source?.close()
  },
  methods: {
    async loadInbox() {
      const requestedProjectId = this.projectId
      const requestId = ++this.inboxRequestId
      try {
        const [notifications, count] = await Promise.all([
          ProjectNotificationApi.list(requestedProjectId),
          ProjectNotificationApi.unreadCount(requestedProjectId),
        ])
        if (requestedProjectId !== this.projectId || requestId !== this.inboxRequestId) return
        this.notifications = notifications.data.items
        this.unreadCount = count.data.unreadCount
      } catch {
        if (requestedProjectId !== this.projectId || requestId !== this.inboxRequestId) return
        this.connectionStatus = '알림을 불러오지 못했습니다.'
      }
    },
    async loadUnreadCount(projectId, requestId) {
      const source = this.source
      try {
        const count = await ProjectNotificationApi.unreadCount(projectId)
        if (projectId !== this.projectId || requestId !== this.inboxRequestId || this.source !== source) return
        this.unreadCount = count.data.unreadCount
      } catch {
        if (projectId !== this.projectId || requestId !== this.inboxRequestId || this.source !== source) return
        this.connectionStatus = '읽지 않은 알림 수를 불러오지 못했습니다.'
      }
    },
    connect() {
      this.source?.close()
      const projectId = this.projectId
      const requestId = this.inboxRequestId
      const source = new EventSource(`/api/projects/${projectId}/notifications/stream`)
      const isCurrent = () => this.source === source && this.projectId === projectId && this.inboxRequestId === requestId
      this.source = source
      source.addEventListener('open', () => { if (isCurrent()) this.connectionStatus = '' })
      source.addEventListener('error', () => { if (isCurrent()) this.connectionStatus = '실시간 알림 연결이 끊겼습니다.' })
      source.addEventListener('notification.created', (event) => {
        if (!isCurrent()) return
        try {
          this.notifications = mergeNotification(this.notifications, JSON.parse(event.data))
          this.loadUnreadCount(projectId, requestId)
        } catch { this.connectionStatus = '실시간 알림을 처리하지 못했습니다.' }
      })
      source.addEventListener('notification.read', (event) => {
        if (!isCurrent()) return
        try {
          const { notificationId, readAt } = JSON.parse(event.data)
          this.notifications = this.notifications.map((item) => item.notificationId === notificationId ? { ...item, readAt } : item)
          this.loadUnreadCount(projectId, requestId)
        } catch { this.connectionStatus = '실시간 알림을 처리하지 못했습니다.' }
      })
      source.addEventListener('reset', () => {
        if (!isCurrent()) return
        this.loadInbox()
        this.connect()
      })
    },
    formatTime(value) {
      if (!value) return '-'
      const date = new Date(value)
      return Number.isNaN(date.getTime()) ? '-' : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
    },
  },
}
</script>
