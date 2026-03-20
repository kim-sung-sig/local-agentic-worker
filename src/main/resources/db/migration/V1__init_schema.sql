CREATE TABLE project (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    local_path  VARCHAR(500) NOT NULL UNIQUE,
    base_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    created_at  TIMESTAMP    NOT NULL
);

CREATE TABLE issue (
    id           UUID         PRIMARY KEY,
    project_id   UUID         NOT NULL REFERENCES project(id),
    issue_number INTEGER      NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    priority     VARCHAR(20)  NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uq_issue_per_project UNIQUE (project_id, issue_number)
);

CREATE INDEX idx_issue_project_id ON issue (project_id);
