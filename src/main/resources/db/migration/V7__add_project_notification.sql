CREATE TABLE project_notification (
    id BIGSERIAL PRIMARY KEY,
    notification_id UUID NOT NULL UNIQUE,
    event_key VARCHAR(255) NOT NULL UNIQUE,
    project_id UUID NOT NULL,
    workflow_run_id UUID,
    type VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    publisher VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_project_notification_cursor ON project_notification(project_id, id);
CREATE INDEX idx_project_notification_unread ON project_notification(project_id, read_at, id);
