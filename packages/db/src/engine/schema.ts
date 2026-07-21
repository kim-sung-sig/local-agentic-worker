import { pgSchema } from 'drizzle-orm/pg-core'

/** The engine named schema is a durable Workflow projection only - never references control_plane. */
export const engineSchema = pgSchema('engine')
