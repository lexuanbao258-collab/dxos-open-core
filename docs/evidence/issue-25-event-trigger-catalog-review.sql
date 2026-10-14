-- READ-ONLY reference queries; no role/schema/ACL changes and no function execution.
-- Prefer scripts/workflow-event-trigger-review.ps1: JDBC verify-full, verified transaction,
-- safe metadata + protected private definitions, no psql requirement.
-- Do not execute issue-25-supabase-approval-proposal.sql as an inspection script.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT pg_catalog.current_setting('transaction_read_only') AS transaction_read_only,
       pg_catalog.current_database() AS database, current_user, session_user,
       pg_catalog.current_setting('session_replication_role') AS session_replication_role;

WITH confirmed(name,event,tags) AS (VALUES
    ('pgrst_ddl_watch','ddl_command_end',NULL::text[]),
    ('pgrst_drop_watch','sql_drop',NULL::text[]),
    ('issue_pg_cron_access','ddl_command_end',ARRAY['CREATE EXTENSION']::text[]),
    ('issue_pg_graphql_access','ddl_command_end',ARRAY['CREATE EXTENSION']::text[]),
    ('issue_pg_net_access','ddl_command_end',ARRAY['CREATE EXTENSION']::text[]),
    ('issue_graphql_placeholder','sql_drop',ARRAY['DROP EXTENSION']::text[]))
SELECT e.evtname,e.evtevent,e.evtenabled,e.evttags,er.rolname AS trigger_owner,
       p.oid AS function_oid,n.nspname AS function_schema,p.proname,fr.rolname AS function_owner,
       l.lanname,p.prosecdef,fr.rolsuper,fr.rolcreaterole,fr.rolcreatedb,fr.rolbypassrls,
       p.proconfig IS NOT NULL AS has_configuration,
       ARRAY(SELECT pg_catalog.split_part(v,'=',1) FROM pg_catalog.unnest(p.proconfig) v ORDER BY v) AS configuration_keys,
       EXISTS(SELECT 1 FROM pg_catalog.unnest(p.proconfig) v WHERE v LIKE 'search_path=%') AS has_search_path,
       pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(pg_catalog.pg_get_functiondef(p.oid),'UTF8')),'hex') AS definition_sha256,
       COALESCE(c.name IS NOT NULL AND c.event=e.evtevent AND c.tags IS NOT DISTINCT FROM e.evttags
           AND er.rolname='supabase_admin' AND n.nspname='extensions',false) AS matches_confirmed_metadata
FROM pg_catalog.pg_event_trigger e
JOIN pg_catalog.pg_proc p ON p.oid=e.evtfoid
JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
JOIN pg_catalog.pg_roles er ON er.oid=e.evtowner
JOIN pg_catalog.pg_roles fr ON fr.oid=p.proowner
JOIN pg_catalog.pg_language l ON l.oid=p.prolang
LEFT JOIN confirmed c ON c.name=e.evtname
WHERE e.evtenabled<>'D' ORDER BY e.evtname;
-- All enabled triggers are included, even unknown names. Missing confirmed names
-- also mean inventory mismatch; exact count/event/tag matches are NOT behavior approval.

SELECT e.evtname,d.refclassid::pg_catalog.regclass AS referenced_catalog,
       d.refobjid,d.refobjsubid,d.deptype
FROM pg_catalog.pg_event_trigger e
JOIN pg_catalog.pg_depend d ON d.classid='pg_catalog.pg_proc'::pg_catalog.regclass AND d.objid=e.evtfoid
WHERE e.evtenabled<>'D' ORDER BY e.evtname,d.refclassid,d.refobjid,d.refobjsubid;
-- pg_depend does NOT prove absence of PL/pgSQL/string-body/dynamic SQL helpers.

SELECT e.evtname,CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE grantee.rolname END AS grantee,
       pg_catalog.pg_get_userbyid(a.grantor) AS grantor,a.privilege_type,a.is_grantable
FROM pg_catalog.pg_event_trigger e JOIN pg_catalog.pg_proc p ON p.oid=e.evtfoid
CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(p.proacl,pg_catalog.acldefault('f',p.proowner))) a
LEFT JOIN pg_catalog.pg_roles grantee ON grantee.oid=a.grantee
WHERE e.evtenabled<>'D' ORDER BY e.evtname,a.grantee,a.grantor;

-- PRIVATE LOCAL REVIEW ONLY; never send results to chat/repository/shared logs.
-- SELECT e.evtname,p.oid,pg_catalog.pg_get_functiondef(p.oid),p.proconfig
-- FROM pg_catalog.pg_event_trigger e JOIN pg_catalog.pg_proc p ON p.oid=e.evtfoid
-- WHERE e.evtenabled<>'D' ORDER BY e.evtname;
-- Re-run PowerShell with -HelperSignature 'schema.helper(argument_types)' or
-- -HelperOid after manually following calls/dynamic SQL in protected body review.
ROLLBACK;
