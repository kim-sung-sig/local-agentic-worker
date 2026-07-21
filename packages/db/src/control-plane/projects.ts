import { sql } from 'drizzle-orm'
import { text, timestamp, uniqueIndex, uuid } from 'drizzle-orm/pg-core'
import { controlPlaneSchema } from './schema.js'
// NOTE: repository_uri intentionally uses a partial uniqueIndex (not a `unique()`
// constraint) because the uniqueness only applies when the value is non-null,
// matching the legacy V6 partial unique index.

/** A registered project (git repository) that issues are filed against. */
export const projects = controlPlaneSchema.table('projects', {
  id: uuid('id').primaryKey().defaultRandom(),
  name: text('name').notNull(),
  localPath: text('local_path'),
  baseBranch: text('base_branch').notNull().default('main'),
  repositoryUri: text('repository_uri'),
  credentialRef: text('credential_ref'),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  uniqueIndex('projects_repository_uri_unique')
    .on(table.repositoryUri)
    .where(sql`${table.repositoryUri} is not null`),
])
