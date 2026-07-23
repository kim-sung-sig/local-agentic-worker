# Python Worker 1 implementation report

Files added:

- `apps/python-agent-worker/pyproject.toml`
- `apps/python-agent-worker/src/agent_worker/app.py`
- `apps/python-agent-worker/src/agent_worker/ledger.py`
- `apps/python-agent-worker/src/agent_worker/models.py`
- `apps/python-agent-worker/tests/test_api.py`

Verification: `cd apps/python-agent-worker && uv run pytest` — 2 passed.

Follow-up validation: empty `credentialRef` and `requestedSourceCommit` now return 422. Focused pytest: 3 passed. Commit: `3045cad`.

Quality fixes: SQLite ledger access is serialized; concurrent duplicate submission returns one ID and the durable three-event sequence. Synchronous fake cancellation explicitly returns 409, and UNC/root-relative Windows paths return 422. The app closes its ledger on shutdown. Focused pytest: 4 passed. Commit: `a51a5c0`.

Assumptions:

- Fake execution completes synchronously, so cancelling an already-completed execution is idempotent and leaves it completed.
- `fake-agent` is the only supported adapter in this slice; capability reporting advertises that deterministic fake.
- SQLite is the durable source for execution identity, status, and ordered events; no runner, Git, or workspace access occurs.

Commit: 9bcd9cf (amended below to record this report)
