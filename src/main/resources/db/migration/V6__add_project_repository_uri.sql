ALTER TABLE project ALTER COLUMN local_path DROP NOT NULL;

ALTER TABLE project ADD COLUMN repository_uri VARCHAR(500);
ALTER TABLE project ADD COLUMN credential_ref VARCHAR(200);

CREATE UNIQUE INDEX uq_project_repository_uri
    ON project (repository_uri)
    WHERE repository_uri IS NOT NULL;
