-- Usage: psql "$WORKER_DATABASE_URL" -v runid='<workflowRunId>' -f dev/verify-ledger.sql
-- Pass the run id with -v runid='...'.

\echo '== executions for run (expect INTAKE COMPLETED and PLANNING COMPLETED) =='
SELECT split_part(idempotency_key, ':', 2) AS stage, status
FROM agent_worker.executions
WHERE idempotency_key LIKE :'runid' || ':%'
ORDER BY idempotency_key;

\echo '== ordered events per execution (expect cursors 1 accepted, 2 running, 3 completed) =='
SELECT split_part(e.idempotency_key, ':', 2) AS stage, ev.cursor, ev.type
FROM agent_worker.execution_events ev
JOIN agent_worker.executions e USING (execution_id)
WHERE e.idempotency_key LIKE :'runid' || ':%'
ORDER BY e.idempotency_key, ev.cursor;

\echo '== leakage scan (every count MUST be 0) =='
SELECT
  (SELECT count(*) FROM agent_worker.executions
     WHERE idempotency_key LIKE :'runid' || ':%'
       AND (idempotency_key LIKE '%leak-sentinel%'
            OR idempotency_key LIKE '%:\%'
            OR artifact_refs::text ILIKE '%workspaceref%'
            OR artifact_refs::text LIKE '%leak-sentinel%')) AS executions_with_leak,
  (SELECT count(*) FROM agent_worker.execution_events ev
     JOIN agent_worker.executions e USING (execution_id)
     WHERE e.idempotency_key LIKE :'runid' || ':%'
       AND (ev.data::text ILIKE '%workspaceref%'
            OR ev.data::text LIKE '%leak-sentinel%')) AS events_with_leak;
