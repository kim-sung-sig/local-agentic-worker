/**
 * Re-exports the two schema namespaces separately. Consumers should import from
 * `@agentic-worker/db/control-plane` or `@agentic-worker/db/engine` style names
 * to keep the schema-ownership boundary explicit; this root index re-exports
 * both namespaced modules for convenience.
 */
export * as controlPlane from './control-plane/index.js'
export * as engine from './engine/index.js'
export { controlPlaneSchema } from './control-plane/schema.js'
export { engineSchema } from './engine/schema.js'
