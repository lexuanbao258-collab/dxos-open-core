-- Immutable Application definition; provision offline in the disposable schema only.
INSERT INTO "${flyway:defaultSchema}".workflow_definitions VALUES ('it-helpdesk',1,'SUBMITTED');
INSERT INTO "${flyway:defaultSchema}".workflow_definition_states VALUES
    ('it-helpdesk',1,'SUBMITTED'),('it-helpdesk',1,'TRIAGED'),('it-helpdesk',1,'IN_PROGRESS'),
    ('it-helpdesk',1,'RESOLVED'),('it-helpdesk',1,'CONFIRMED');
INSERT INTO "${flyway:defaultSchema}".workflow_definition_transitions VALUES
    ('it-helpdesk',1,'triage','SUBMITTED','TRIAGED'),
    ('it-helpdesk',1,'start_work','TRIAGED','IN_PROGRESS'),
    ('it-helpdesk',1,'resolve','IN_PROGRESS','RESOLVED'),
    ('it-helpdesk',1,'confirm','RESOLVED','CONFIRMED');
