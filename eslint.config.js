import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: [
      '**/node_modules/**',
      '**/.nuxt/**',
      '**/.output/**',
      '**/dist/**',
      'build/**',
      'src/**',
      'frontend/**',
      'contracts/**',
      'control-plane-app/**',
      'agent-engine-app/**',
      'apps/control-plane/**',
    ],
  },
  {
    files: [
      'packages/*/src/**/*.ts',
      'packages/*/test/**/*.ts',
      'apps/temporal-worker/src/**/*.ts',
      'apps/temporal-worker/test/**/*.ts',
    ],
    extends: [tseslint.configs.recommended],
  },
)
