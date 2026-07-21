import { defineConfig } from 'drizzle-kit'

/**
 * Schema-first config for the engine schema only.
 * Generate-only: no `push` path is defined anywhere in this config.
 */
export default defineConfig({
  dialect: 'postgresql',
  schema: './src/engine/index.ts',
  out: './drizzle/engine',
})
