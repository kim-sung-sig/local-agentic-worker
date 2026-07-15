CREATE TABLE engine_workflow_run (
    id                    UUID PRIMARY KEY,
    ticket_id             UUID NOT NULL,
    temporal_workflow_id  VARCHAR(200) NOT NULL,
    current_stage         VARCHAR(30) NOT NULL,
    status                VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    workspace_ref         VARCHAR(500),
    started_at            TIMESTAMP NOT NULL,
    finished_at           TIMESTAMP,
    CONSTRAINT uq_engine_workflow_run_temporal_id UNIQUE (temporal_workflow_id)
);

CREATE TABLE engine_stage_gate (
    workflow_run_id  UUID NOT NULL REFERENCES engine_workflow_run(id),
    stage            VARCHAR(30) NOT NULL,
    decision         VARCHAR(30) NOT NULL,
    reason           TEXT,
    decided_at       TIMESTAMP NOT NULL
);

CREATE TABLE engine_attempt_record (
    id                            UUID PRIMARY KEY,
    workflow_run_id               UUID NOT NULL REFERENCES engine_workflow_run(id),
    attempt_number                INT NOT NULL,
    implementation_artifact_ref   TEXT,
    qa_report_ref                 TEXT,
    qa_score                      INT,
    status                        VARCHAR(30) NOT NULL,
    created_at                    TIMESTAMP NOT NULL,
    finished_at                   TIMESTAMP,
    CONSTRAINT uq_engine_attempt_run_number UNIQUE (workflow_run_id, attempt_number)
);

CREATE INDEX idx_engine_stage_gate_run_id ON engine_stage_gate(workflow_run_id);
CREATE INDEX idx_engine_attempt_record_run_id ON engine_attempt_record(workflow_run_id);
