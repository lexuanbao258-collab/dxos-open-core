-- PostgreSQL 17, catalog-only: no DDL/DML/Flyway/smoke. Use approved verify-full TLS.
-- Repeat with genuine migrator/runtime logins; SET ROLE is not login evidence.
-- Missing roles/schema/objects mean NOT_PROVISIONED, never permission PASS.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT pg_catalog.current_database() AS database, session_user, current_user,
       pg_catalog.current_setting('server_version') AS server_version,
       pg_catalog.current_setting('transaction_read_only') AS transaction_read_only,
       pg_catalog.current_setting('search_path') AS search_path,
       pg_catalog.current_setting('createrole_self_grant', true) AS createrole_self_grant,
       pg_catalog.current_setting('session_replication_role') AS session_replication_role,
       pg_catalog.current_setting('event_triggers', true) AS event_triggers,
       (SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid = pg_catalog.pg_backend_pid()) AS backend_ssl,
       pg_catalog.to_regnamespace('dxos_wf25_smoke_01') IS NOT NULL AS schema_present,
       EXISTS (SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
               WHERE n.nspname = 'dxos_wf25_smoke_01' AND c.relname = 'flyway_schema_history') AS history_present;
SELECT wanted.name, CASE WHEN r.oid IS NULL THEN 'NOT_PROVISIONED' ELSE 'PRESENT' END AS status,
       r.rolcanlogin, r.rolsuper, r.rolcreatedb, r.rolcreaterole, r.rolreplication, r.rolbypassrls, r.rolinherit
FROM (VALUES ('postgres'), ('dxos_wf25_migrator'), ('dxos_wf25_runtime')) wanted(name)
LEFT JOIN pg_catalog.pg_roles r ON r.rolname = wanted.name ORDER BY wanted.name;

-- Effective database rights include PUBLIC/inherited roles. PostgreSQL has no deny ACL.
SELECT r.rolname, p.privilege,
       pg_catalog.has_database_privilege(r.oid, d.oid, p.privilege) AS effective,
       pg_catalog.has_database_privilege(r.oid, d.oid, p.privilege || ' WITH GRANT OPTION') AS grantable
FROM pg_catalog.pg_roles r CROSS JOIN pg_catalog.pg_database d
CROSS JOIN (VALUES ('CONNECT'), ('CREATE'), ('TEMP')) p(privilege)
WHERE r.rolname IN ('dxos_wf25_migrator', 'dxos_wf25_runtime') AND d.datname = pg_catalog.current_database()
ORDER BY r.rolname, p.privilege;
SELECT d.datname, a.privilege_type, a.is_grantable
FROM pg_catalog.pg_database d CROSS JOIN LATERAL pg_catalog.aclexplode(
    COALESCE(d.datacl, pg_catalog.acldefault('d', d.datdba))) a
WHERE d.datname = pg_catalog.current_database() AND a.grantee = 0 ORDER BY a.privilege_type;
SELECT r.rolname, n.nspname, pg_catalog.pg_get_userbyid(n.nspowner) AS owner,
       pg_catalog.has_schema_privilege(r.oid, n.oid, 'USAGE') AS usage,
       pg_catalog.has_schema_privilege(r.oid, n.oid, 'CREATE') AS create,
       pg_catalog.has_schema_privilege(r.oid, n.oid, 'USAGE WITH GRANT OPTION') AS usage_grantable,
       pg_catalog.has_schema_privilege(r.oid, n.oid, 'CREATE WITH GRANT OPTION') AS create_grantable
FROM pg_catalog.pg_roles r CROSS JOIN pg_catalog.pg_namespace n
WHERE r.rolname IN ('dxos_wf25_migrator', 'dxos_wf25_runtime')
  AND (n.nspname = 'dxos_wf25_smoke_01' OR pg_catalog.has_schema_privilege(r.oid, n.oid, 'CREATE'))
-- PUBLIC CREATE can affect future runtime roles even when neither role exists yet.
UNION ALL
SELECT 'PUBLIC', n.nspname, pg_catalog.pg_get_userbyid(n.nspowner),
       pg_catalog.bool_or(a.privilege_type = 'USAGE'), pg_catalog.bool_or(a.privilege_type = 'CREATE'),
       pg_catalog.bool_or(a.privilege_type = 'USAGE' AND a.is_grantable),
       pg_catalog.bool_or(a.privilege_type = 'CREATE' AND a.is_grantable)
FROM pg_catalog.pg_namespace n CROSS JOIN LATERAL pg_catalog.aclexplode(
    COALESCE(n.nspacl, pg_catalog.acldefault('n', n.nspowner))) a
WHERE a.grantee = 0 GROUP BY n.oid, n.nspname, n.nspowner
HAVING pg_catalog.bool_or(a.privilege_type = 'CREATE') OR n.nspname = 'dxos_wf25_smoke_01'
ORDER BY 1, 2;

-- ALL membership paths/edge options. ADMIN is not an AND along a path.
WITH RECURSIVE paths AS (
    SELECT r.oid AS origin, r.oid AS reached, ARRAY[r.oid] AS visited,
           ARRAY[r.rolname::text] AS names, true AS inherit_path, true AS set_path
    FROM pg_catalog.pg_roles r WHERE r.rolname IN ('dxos_wf25_migrator', 'dxos_wf25_runtime')
    UNION ALL
    SELECT p.origin, m.roleid, p.visited || m.roleid, p.names || granted.rolname::text,
           p.inherit_path AND m.inherit_option, p.set_path AND m.set_option
    FROM paths p JOIN pg_catalog.pg_auth_members m ON m.member = p.reached
    JOIN pg_catalog.pg_roles granted ON granted.oid = m.roleid
    WHERE NOT m.roleid = ANY(p.visited)
)
SELECT names AS membership_path, inherit_path, set_path,
       pg_catalog.pg_has_role(origin, reached, 'USAGE') AS effective_inherit,
       pg_catalog.pg_has_role(origin, reached, 'SET') AS effective_set,
       pg_catalog.pg_has_role(origin, reached, 'MEMBER WITH ADMIN OPTION') AS effective_admin
FROM paths WHERE origin <> reached ORDER BY names;
SELECT granted.rolname AS granted_role, member.rolname AS member, grantor.rolname AS grantor,
       m.admin_option, m.inherit_option, m.set_option
FROM pg_catalog.pg_auth_members m
JOIN pg_catalog.pg_roles granted ON granted.oid = m.roleid
JOIN pg_catalog.pg_roles member ON member.oid = m.member
JOIN pg_catalog.pg_roles grantor ON grantor.oid = m.grantor
WHERE member.rolname IN ('postgres', 'dxos_wf25_migrator', 'dxos_wf25_runtime')
   OR granted.rolname IN ('dxos_wf25_migrator', 'dxos_wf25_runtime')
ORDER BY granted.rolname, member.rolname, grantor.rolname;

-- Full table/column matrices, including Flyway history and API-role leakage.
SELECT r.rolname, c.relname, pg_catalog.pg_get_userbyid(c.relowner) AS owner, p.privilege,
       pg_catalog.has_table_privilege(r.oid, c.oid, p.privilege) AS effective,
       pg_catalog.has_table_privilege(r.oid, c.oid, p.privilege || ' WITH GRANT OPTION') AS grantable
FROM pg_catalog.pg_roles r CROSS JOIN pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'),
                   ('TRIGGER'), ('REFERENCES'), ('MAINTAIN')) p(privilege)
WHERE r.rolname IN ('dxos_wf25_runtime', 'anon', 'authenticated', 'service_role')
  AND n.nspname = 'dxos_wf25_smoke_01' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
ORDER BY r.rolname, c.relname, p.privilege;
-- A false table right does not prove denial of the corresponding column right.
SELECT r.rolname, c.relname, a.attname, p.privilege,
       pg_catalog.has_column_privilege(r.oid, c.oid, a.attnum, p.privilege) AS effective,
       pg_catalog.has_column_privilege(r.oid, c.oid, a.attnum, p.privilege || ' WITH GRANT OPTION') AS grantable
FROM pg_catalog.pg_roles r CROSS JOIN pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('REFERENCES')) p(privilege)
WHERE r.rolname IN ('dxos_wf25_runtime', 'anon', 'authenticated', 'service_role')
  AND n.nspname = 'dxos_wf25_smoke_01' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
ORDER BY r.rolname, c.relname, a.attnum, p.privilege;

-- Report existing runtime rights outside the private schema; never revoke them here.
SELECT n.nspname, c.relname, p.privilege,
       pg_catalog.has_table_privilege(r.oid, c.oid, p.privilege) AS table_right,
       CASE WHEN p.privilege IN ('SELECT', 'INSERT', 'UPDATE', 'REFERENCES')
            THEN pg_catalog.has_any_column_privilege(r.oid, c.oid, p.privilege) ELSE false END AS column_right,
       pg_catalog.has_table_privilege(r.oid, c.oid, p.privilege || ' WITH GRANT OPTION') AS table_grantable,
       CASE WHEN p.privilege IN ('SELECT', 'INSERT', 'UPDATE', 'REFERENCES')
            THEN pg_catalog.has_any_column_privilege(r.oid, c.oid, p.privilege || ' WITH GRANT OPTION') ELSE false END AS column_grantable
FROM pg_catalog.pg_roles r CROSS JOIN pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'),
                   ('TRIGGER'), ('REFERENCES'), ('MAINTAIN')) p(privilege)
WHERE r.rolname = 'dxos_wf25_runtime' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
  AND n.nspname <> 'dxos_wf25_smoke_01' AND n.nspname <> 'information_schema' AND n.nspname !~ '^pg_'
  AND (pg_catalog.has_table_privilege(r.oid, c.oid, p.privilege)
       OR CASE WHEN p.privilege IN ('SELECT', 'INSERT', 'UPDATE', 'REFERENCES')
               THEN pg_catalog.has_any_column_privilege(r.oid, c.oid, p.privilege) ELSE false END)
ORDER BY n.nspname, c.relname, p.privilege;
SELECT n.nspname, c.relname, p.privilege,
       pg_catalog.has_sequence_privilege(r.oid, c.oid, p.privilege) AS effective,
       pg_catalog.has_sequence_privilege(r.oid, c.oid, p.privilege || ' WITH GRANT OPTION') AS grantable
FROM pg_catalog.pg_roles r CROSS JOIN pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
CROSS JOIN (VALUES ('USAGE'), ('SELECT'), ('UPDATE')) p(privilege)
WHERE r.rolname = 'dxos_wf25_runtime' AND c.relkind = 'S' AND n.nspname !~ '^pg_'
  AND pg_catalog.has_sequence_privilege(r.oid, c.oid, p.privilege)
ORDER BY n.nspname, c.relname, p.privilege;

-- Ownership outside this schema also violates a strictly limited runtime role.
SELECT 'database' AS kind, d.datname AS object, pg_catalog.pg_get_userbyid(d.datdba) AS owner
FROM pg_catalog.pg_database d WHERE pg_catalog.pg_get_userbyid(d.datdba) = 'dxos_wf25_runtime'
UNION ALL SELECT 'schema', n.nspname, pg_catalog.pg_get_userbyid(n.nspowner)
FROM pg_catalog.pg_namespace n WHERE pg_catalog.pg_get_userbyid(n.nspowner) = 'dxos_wf25_runtime'
UNION ALL SELECT 'relation', n.nspname || '.' || c.relname, pg_catalog.pg_get_userbyid(c.relowner)
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE pg_catalog.pg_get_userbyid(c.relowner) = 'dxos_wf25_runtime'
UNION ALL SELECT 'routine', p.oid::pg_catalog.regprocedure::text, pg_catalog.pg_get_userbyid(p.proowner)
FROM pg_catalog.pg_proc p WHERE pg_catalog.pg_get_userbyid(p.proowner) = 'dxos_wf25_runtime'
UNION ALL SELECT 'type', n.nspname || '.' || t.typname, pg_catalog.pg_get_userbyid(t.typowner)
FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
WHERE pg_catalog.pg_get_userbyid(t.typowner) = 'dxos_wf25_runtime';

-- No function source/config values printed. Review ALL callable runtime routines,
-- including SECURITY INVOKER builtins such as pg_read_file; ordinary builtin EXECUTE
-- is not automatically a blocker. Classify capabilities semantically, never auto-REVOKE.
SELECT r.rolname, p.oid::pg_catalog.regprocedure AS routine,
       pg_catalog.pg_get_userbyid(p.proowner) AS owner, p.prosecdef,
       pg_catalog.has_function_privilege(r.oid, p.oid, 'EXECUTE') AS execute,
       pg_catalog.has_function_privilege(r.oid, p.oid, 'EXECUTE WITH GRANT OPTION') AS grantable
FROM pg_catalog.pg_roles r CROSS JOIN pg_catalog.pg_proc p
JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
WHERE (r.rolname = 'dxos_wf25_runtime' AND
       (n.nspname = 'dxos_wf25_smoke_01' OR pg_catalog.has_function_privilege(r.oid, p.oid, 'EXECUTE')))
   OR (r.rolname IN ('anon', 'authenticated', 'service_role') AND
       (n.nspname = 'dxos_wf25_smoke_01' OR (p.prosecdef AND pg_catalog.has_function_privilege(r.oid, p.oid, 'EXECUTE'))))
-- PUBLIC callability applies to future roles even before provisioning.
UNION ALL
SELECT 'PUBLIC', p.oid::pg_catalog.regprocedure, pg_catalog.pg_get_userbyid(p.proowner),
       p.prosecdef, true, pg_catalog.bool_or(a.is_grantable)
FROM pg_catalog.pg_proc p CROSS JOIN LATERAL pg_catalog.aclexplode(
    COALESCE(p.proacl, pg_catalog.acldefault('f', p.proowner))) a
WHERE p.prosecdef AND a.grantee = 0 AND a.privilege_type = 'EXECUTE'
GROUP BY p.oid, p.proowner, p.prosecdef
ORDER BY 1, 2;
SELECT creator.rolname AS creator, COALESCE(n.nspname, '<GLOBAL>') AS scope, d.defaclobjtype,
       CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE grantee.rolname END AS grantee,
       a.privilege_type, a.is_grantable
FROM pg_catalog.pg_default_acl d JOIN pg_catalog.pg_roles creator ON creator.oid = d.defaclrole
LEFT JOIN pg_catalog.pg_namespace n ON n.oid = d.defaclnamespace
CROSS JOIN LATERAL pg_catalog.aclexplode(d.defaclacl) a
LEFT JOIN pg_catalog.pg_roles grantee ON grantee.oid = a.grantee
WHERE creator.rolname IN ('postgres', 'dxos_wf25_migrator', 'dxos_wf25_runtime')
  AND (d.defaclnamespace = 0 OR n.nspname = 'dxos_wf25_smoke_01')
ORDER BY creator.rolname, scope, d.defaclobjtype, grantee, a.privilege_type;
SELECT c.relname, pg_catalog.pg_get_userbyid(c.relowner) AS owner, a.attname,
       pg_catalog.format_type(a.atttypid, a.atttypmod) AS type
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
WHERE n.nspname = 'dxos_wf25_smoke_01' AND c.relname = 'flyway_schema_history' ORDER BY a.attnum;
-- After history_present=true, only verified migrator may separately SELECT
-- installed_rank, version, description, type, script, checksum, success FROM
-- dxos_wf25_smoke_01.flyway_schema_history ORDER BY installed_rank;
-- Match exactly successful V3/V4 and pinned checksums via the existing isolated runner.
-- Expected ACL: 5 SELECT tables; INSERT instances/history; exactly 5 UPDATE columns
-- on instances. All grant options/other writes/ownership/CREATE/SET paths denied;
-- history and API roles have no table OR column rights. PUBLIC TEMP is a blocker
-- unless a separate reviewed policy exception changes the required boundary.
ROLLBACK;
