import { pgSchema } from 'drizzle-orm/pg-core'

/** The control_plane named schema owns projects, issues, documents, and auth/notification tables. */
export const controlPlaneSchema = pgSchema('control_plane')
