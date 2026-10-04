ALTER TABLE requests
    ADD COLUMN assignee_id VARCHAR(100),
    ADD COLUMN resolution VARCHAR(4000),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT requests_assignee_not_blank CHECK (assignee_id ~ '[^[:space:]]'),
    ADD CONSTRAINT requests_resolution_not_blank CHECK (resolution ~ '[^[:space:]]'),
    ADD CONSTRAINT requests_version_valid CHECK (version >= 0);

CREATE TABLE request_status_history (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES requests(id),
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    action VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT request_status_history_transition_valid CHECK (
        (from_status = 'NEW' AND to_status = 'ASSIGNED' AND action = 'ASSIGN') OR
        (from_status = 'ASSIGNED' AND to_status = 'IN_PROGRESS' AND action = 'START') OR
        (from_status = 'IN_PROGRESS' AND to_status = 'RESOLVED' AND action = 'RESOLVE') OR
        (from_status = 'RESOLVED' AND to_status = 'CONFIRMED' AND action = 'CONFIRM') OR
        (from_status = 'CONFIRMED' AND to_status = 'CLOSED' AND action = 'CLOSE')
    )
);

CREATE INDEX request_status_history_chronological_idx
    ON request_status_history (request_id, changed_at, id);
