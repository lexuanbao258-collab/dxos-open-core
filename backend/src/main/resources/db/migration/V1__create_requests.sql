CREATE TABLE requests (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    request_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT requests_title_not_blank CHECK (title ~ '[^[:space:]]'),
    CONSTRAINT requests_description_not_blank CHECK (description ~ '[^[:space:]]'),
    CONSTRAINT requests_type_not_blank CHECK (request_type ~ '[^[:space:]]'),
    CONSTRAINT requests_status_valid CHECK (status = 'NEW'),
    CONSTRAINT requests_timestamps_valid CHECK (updated_at >= created_at)
);
