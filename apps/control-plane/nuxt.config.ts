export default defineNuxtConfig({
  compatibilityDate: '2026-07-20',
  devtools: { enabled: true },
  runtimeConfig: {
    databaseUrl: process.env.DATABASE_URL || 'postgresql://dev_user:dev_password@localhost:15432/agentic_worker',
  },
})
