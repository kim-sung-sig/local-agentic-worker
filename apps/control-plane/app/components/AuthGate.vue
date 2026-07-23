<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useControlPlaneApi } from '../composables/control-plane'

const emit = defineEmits<{ authenticated: [] }>()
const api = useControlPlaneApi()
const mode = ref<'login' | 'register'>('login')
const error = ref('')
const submitting = ref(false)
const form = reactive({ email: '', password: '', name: '' })

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    if (mode.value === 'register') {
      await api.register({ ...form, name: form.name || undefined })
    } else {
      await api.login({ email: form.email, password: form.password })
    }
    emit('authenticated')
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'Authentication failed. Please try again.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-gate" aria-labelledby="auth-title">
    <form class="auth-card" @submit.prevent="submit">
      <p class="auth-kicker">Agentic Worker</p>
      <h1 id="auth-title">{{ mode === 'login' ? 'Sign in' : 'Create your account' }}</h1>
      <p>Sign in to access your control plane projects.</p>

      <label v-if="mode === 'register'" for="auth-name">
        Name
        <input id="auth-name" v-model="form.name" autocomplete="name" />
      </label>
      <label for="auth-email">
        Email
        <input id="auth-email" v-model="form.email" type="email" autocomplete="email" required />
      </label>
      <label for="auth-password">
        Password
        <input id="auth-password" v-model="form.password" type="password" autocomplete="current-password" minlength="8" required />
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="button button-primary" :disabled="submitting" type="submit">
        {{ submitting ? 'Working…' : mode === 'login' ? 'Sign in' : 'Create account' }}
      </button>
      <button class="text-button" type="button" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? 'Need an account? Register' : 'Already have an account? Sign in' }}
      </button>
    </form>
  </main>
</template>
