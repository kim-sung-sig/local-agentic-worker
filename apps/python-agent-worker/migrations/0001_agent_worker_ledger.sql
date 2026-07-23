CREATE SCHEMA IF NOT EXISTS agent_worker;

CREATE TABLE IF NOT EXISTS agent_worker.executions (
  execution_id UUID PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL CHECK (status IN ('ACCEPTED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
  artifact_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_worker.execution_events (
  execution_id UUID NOT NULL REFERENCES agent_worker.executions (execution_id),
  cursor INTEGER NOT NULL CHECK (cursor > 0),
  type TEXT NOT NULL CHECK (type IN ('accepted', 'running', 'completed', 'failed', 'cancelled')),
  data JSONB NOT NULL DEFAULT '{}'::jsonb,
  PRIMARY KEY (execution_id, cursor)
);
