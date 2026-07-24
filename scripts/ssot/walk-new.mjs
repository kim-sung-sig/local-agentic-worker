#!/usr/bin/env node
// SSoT walk-feature scaffolder — platform-neutral, zero-dependency.
// Shared by Claude Code (.claude/commands/ssot) and Codex (.codex/prompts/ssot).
// Usage: node scripts/ssot/walk-new.mjs <app> <domain> <feature-slug>
//   e.g. node scripts/ssot/walk-new.mjs control-plane issue attach-labels
// Creates apps/<app>/docs/walk/<domain>/feature/<slug>/ from the app's walk/_TEMPLATE.

import { cp, mkdir, stat, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, join, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(__dirname, '..', '..')

function fail(msg) {
  console.error(`error: ${msg}`)
  process.exit(1)
}

async function exists(p) {
  try {
    await stat(p)
    return true
  } catch {
    return false
  }
}

const [app, domain, slug] = process.argv.slice(2)
if (!app || !domain || !slug) {
  fail('usage: node scripts/ssot/walk-new.mjs <app> <domain> <feature-slug>')
}
if (!/^[a-z0-9][a-z0-9-]*$/.test(slug)) {
  fail(`feature-slug must be kebab-case: "${slug}"`)
}

const appDocs = join(repoRoot, 'apps', app, 'docs')
if (!(await exists(appDocs))) {
  fail(`app docs not found: apps/${app}/docs (is "${app}" SSoT-enabled?)`)
}

const template = join(appDocs, 'walk', '_TEMPLATE')
if (!(await exists(template))) {
  fail(`walk template not found: ${template}`)
}

const target = join(appDocs, 'walk', domain, 'feature', slug)
if (await exists(target)) {
  fail(`feature already exists (not overwriting): ${target}`)
}

await mkdir(dirname(target), { recursive: true })
await cp(template, target, { recursive: true })

// Stamp the feature identity into plan.md if it carries the placeholder.
const planPath = join(target, 'plan.md')
if (await exists(planPath)) {
  const body = await readFile(planPath, 'utf8')
  await writeFile(planPath, body.replaceAll('{{FEATURE}}', slug).replaceAll('{{DOMAIN}}', domain))
}

const rel = target.slice(repoRoot.length + 1).replaceAll('\\', '/')
console.log(`created walk feature: ${rel}`)
console.log(`  next: fill ${rel}/plan.md, then design.md`)
