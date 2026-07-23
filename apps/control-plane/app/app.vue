<script setup lang="ts">
import { onMounted, ref } from 'vue'
import '~/assets/app.css'
import { isUnauthenticatedError, useControlPlaneApi } from './composables/control-plane'

const api = useControlPlaneApi()
const sessionState = ref<'loading' | 'authenticated' | 'unauthenticated' | 'error'>('loading')
const sessionError = ref('')

async function restoreSession() {
  sessionState.value = 'loading'
  sessionError.value = ''
  try {
    await api.listProjects()
    sessionState.value = 'authenticated'
  } catch (cause: unknown) {
    if (isUnauthenticatedError(cause)) {
      sessionState.value = 'unauthenticated'
      return
    }
    sessionError.value = cause instanceof Error ? cause.message : 'Could not restore the session.'
    sessionState.value = 'error'
  }
}

onMounted(restoreSession)
</script>

<template>
  <main v-if="sessionState === 'loading'" class="screen-message" role="status" aria-live="polite">
    Restoring your session…
  </main>
  <AuthGate v-else-if="sessionState === 'unauthenticated'" @authenticated="sessionState = 'authenticated'" />
  <main v-else-if="sessionState === 'error'" class="screen-message" role="alert">
    <p>{{ sessionError }}</p>
    <button class="button button-primary" type="button" @click="restoreSession">Try again</button>
  </main>
  <div v-else class="app-shell">
    <aside class="side-nav">
      <NuxtLink class="brand" to="/"><span class="brand-mark">◆</span> Agentic Worker</NuxtLink>
      <nav class="primary-nav" aria-label="Primary navigation">
        <NuxtLink to="/">Dashboard</NuxtLink>
      </nav>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div><strong>Control Plane</strong><p>Projects and issues</p></div>
      </header>
      <main class="app-content"><NuxtPage /></main>
    </div>
  </div>
</template>
