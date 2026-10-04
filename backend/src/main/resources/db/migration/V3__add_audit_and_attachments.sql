CREATE TABLE request_audit (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES requests(id),
    action VARCHAR(40) NOT NULL,
    actor_ref VARCHAR(100) NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT request_audit_action_valid CHECK (action IN (
        'REQUEST_CREATED', 'REQUEST_ASSIGNED', 'REQUEST_STARTED', 'REQUEST_RESOLVED',
        'REQUEST_CONFIRMED', 'REQUEST_CLOSED', 'ATTACHMENT_ADDED'
    )),
    CONSTRAINT request_audit_actor_not_blank CHECK (actor_ref ~ '[^[:space:]]')
);

CREATE INDEX request_audit_chronological_idx ON request_audit (request_id, created_at, id);

CREATE TABLE request_attachments (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES requests(id),
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size BIGINT NOT NULL,
    storage_key VARCHAR(120) NOT NULL UNIQUE,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT request_attachments_filename_not_blank CHECK (original_filename ~ '[^[:space:]]'),
    CONSTRAINT request_attachments_size_valid CHECK (size > 0),
    CONSTRAINT request_attachments_key_not_blank CHECK (storage_key ~ '[^[:space:]]')
);

CREATE INDEX request_attachments_chronological_idx ON request_attachments (request_id, uploaded_at, id);
