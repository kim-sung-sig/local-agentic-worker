CREATE TABLE agent_job (
    id            UUID PRIMARY KEY,
    issue_id      UUID NOT NULL,
    project_id    UUID NOT NULL,
    branch_name   VARCHAR(200) NOT NULL,
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    started_at    TIMESTAMP    NOT NULL,
    finished_at   TIMESTAMP,
    error_message TEXT,
    pr_url        VARCHAR(500),
    CONSTRAINT fk_agent_job_issue FOREIGN KEY (issue_id) REFERENCES issue(id)
);

CREATE INDEX idx_agent_job_issue_id ON agent_job(issue_id);
