import { defineConfig } from 'drizzle-kit'

/**
 * Schema-first config for the control_plane schema only.
 * Generate-only: no `push` path is defined anywhere in this config.
 */
export default defineConfig({
  dialect: 'postgresql',
  schema: './src/control-plane/index.ts',
  out: './drizzle/control-plane',
})
