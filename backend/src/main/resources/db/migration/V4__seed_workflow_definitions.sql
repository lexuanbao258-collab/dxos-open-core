-- Independent, version-pinned examples; new content requires a new version.
INSERT INTO "${flyway:defaultSchema}".workflow_definitions (definition_id, definition_version, initial_state) VALUES
    ('document-review', 1, 'DRAFT'), ('incident-resolution', 1, 'OPEN');

INSERT INTO "${flyway:defaultSchema}".workflow_definition_states (definition_id, definition_version, state_id) VALUES
    ('document-review', 1, 'DRAFT'), ('document-review', 1, 'IN_REVIEW'),
    ('document-review', 1, 'APPROVED'), ('document-review', 1, 'REJECTED'),
    ('incident-resolution', 1, 'OPEN'), ('incident-resolution', 1, 'IN_PROGRESS'),
    ('incident-resolution', 1, 'RESOLVED');

INSERT INTO "${flyway:defaultSchema}".workflow_definition_transitions
    (definition_id, definition_version, transition_id, source_state, target_state) VALUES
    ('document-review', 1, 'submit', 'DRAFT', 'IN_REVIEW'),
    ('document-review', 1, 'approve', 'IN_REVIEW', 'APPROVED'),
    ('document-review', 1, 'reject', 'IN_REVIEW', 'REJECTED'),
    ('incident-resolution', 1, 'start', 'OPEN', 'IN_PROGRESS'),
    ('incident-resolution', 1, 'resolve', 'IN_PROGRESS', 'RESOLVED');
