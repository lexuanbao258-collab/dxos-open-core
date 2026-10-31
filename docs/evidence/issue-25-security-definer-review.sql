-- PostgreSQL 17 catalog capture only. Never call discovered routines.
-- JDBC binds the manual-helper oid[] in each selected-routine query.
-- The last SELECT resolves a bound helper signature; it is not a routine call.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

SELECT pg_catalog.current_setting('server_version') AS server_version,
       pg_catalog.current_setting('transaction_read_only') AS transaction_read_only,
       pg_catalog.current_setting('search_path') AS search_path,
       pg_catalog.current_database() AS database_name,
       session_user AS session_user, current_user AS current_user,
       (SELECT ssl FROM pg_catalog.pg_stat_ssl
        WHERE pid = pg_catalog.pg_backend_pid()) AS backend_ssl;

SELECT wanted.name,
       CASE WHEN r.oid IS NULL THEN 'NOT_PROVISIONED' ELSE 'PRESENT' END AS status,
       r.rolcanlogin, r.rolsuper, r.rolcreaterole, r.rolcreatedb, r.rolbypassrls
FROM (VALUES ('dxos_wf25_migrator'), ('dxos_wf25_runtime')) AS wanted(name)
LEFT JOIN pg_catalog.pg_roles r ON r.rolname = wanted.name ORDER BY wanted.name;

SELECT a.privilege_type, a.is_grantable
FROM pg_catalog.pg_database d CROSS JOIN LATERAL pg_catalog.aclexplode(
    COALESCE(d.datacl, pg_catalog.acldefault('d', d.datdba))) a
WHERE d.datname = pg_catalog.current_database() AND a.grantee = 0
ORDER BY a.privilege_type;

SELECT p.oid, n.nspname AS schema_name, p.proname AS routine_name,
       pg_catalog.pg_get_function_identity_arguments(p.oid) AS identity_arguments,
       pg_catalog.format('%I.%I(%s)', n.nspname, p.proname,
           pg_catalog.pg_get_function_identity_arguments(p.oid)) AS signature,
       o.rolname AS owner, o.rolsuper AS owner_superuser,
       o.rolbypassrls AS owner_bypass_rls, o.rolcreaterole AS owner_create_role,
       o.rolcreatedb AS owner_create_db, o.rolreplication AS owner_replication,
       l.lanname AS language, extension.extname AS extension_name, extension.extversion AS extension_version,
       p.prosecdef AS security_definer, p.prokind AS routine_kind,
       pg_catalog.format_type(p.prorettype, NULL) AS return_type,
       CASE WHEN p.prorettype IN ('pg_catalog.trigger'::pg_catalog.regtype,
                                 'pg_catalog.event_trigger'::pg_catalog.regtype)
            THEN 'TRIGGER_CONTEXT_ONLY' ELSE 'ORDINARY_CALL' END AS call_context,
       COALESCE(p.proacl, pg_catalog.acldefault('f', p.proowner))::pg_catalog.text AS execute_acl,
       (SELECT pg_catalog.substr(c.setting, 13)
        FROM pg_catalog.unnest(p.proconfig) AS c(setting)
        WHERE c.setting LIKE 'search_path=%') AS function_search_path,
       pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
           pg_catalog.pg_get_functiondef(p.oid), 'UTF8')), 'hex') AS definition_sha256,
       pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
           pg_catalog.array_to_json(COALESCE(p.proconfig, ARRAY[]::pg_catalog.text[]))::pg_catalog.text,
           'UTF8')), 'hex') AS configuration_sha256,
       EXISTS (SELECT 1 FROM pg_catalog.aclexplode(
           COALESCE(p.proacl, pg_catalog.acldefault('f', p.proowner))) a
           WHERE a.grantee = 0 AND a.privilege_type = 'EXECUTE') AS public_execute,
       EXISTS (SELECT 1 FROM pg_catalog.aclexplode(
           COALESCE(n.nspacl, pg_catalog.acldefault('n', n.nspowner))) a
           WHERE a.grantee = 0 AND a.privilege_type = 'USAGE') AS public_schema_usage,
       r.oid IS NOT NULL AS runtime_exists,
       CASE WHEN r.oid IS NOT NULL THEN
           pg_catalog.has_function_privilege(r.oid, p.oid, 'EXECUTE') END AS runtime_execute,
       CASE WHEN r.oid IS NOT NULL THEN
           pg_catalog.has_schema_privilege(r.oid, n.oid, 'USAGE') END AS runtime_schema_usage,
       CASE WHEN r.oid IS NOT NULL THEN
           pg_catalog.pg_has_role(r.oid, o.oid, 'USAGE') END AS runtime_inherits_owner,
       CASE WHEN r.oid IS NOT NULL THEN
           pg_catalog.pg_has_role(r.oid, o.oid, 'SET') END AS runtime_can_set_owner,
       CASE WHEN s.oid IS NOT NULL THEN
           pg_catalog.has_function_privilege(s.oid, p.oid, 'EXECUTE') END AS service_role_execute,
       CASE WHEN s.oid IS NOT NULL THEN
           pg_catalog.has_schema_privilege(s.oid, n.oid, 'USAGE') END AS service_role_schema_usage
FROM pg_catalog.pg_proc p
JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
JOIN pg_catalog.pg_roles o ON o.oid = p.proowner
JOIN pg_catalog.pg_language l ON l.oid = p.prolang
LEFT JOIN pg_catalog.pg_roles r ON r.rolname = 'dxos_wf25_runtime'
LEFT JOIN pg_catalog.pg_roles s ON s.rolname = 'service_role'
LEFT JOIN LATERAL (
    SELECT e.extname, e.extversion FROM pg_catalog.pg_depend d
    JOIN pg_catalog.pg_extension e ON e.oid = d.refobjid
    WHERE d.classid = 'pg_catalog.pg_proc'::pg_catalog.regclass AND d.objid = p.oid
      AND d.refclassid = 'pg_catalog.pg_extension'::pg_catalog.regclass AND d.deptype = 'e'
) extension ON true
WHERE p.prosecdef OR p.oid = ANY (?::pg_catalog.oid[])
ORDER BY n.nspname, p.proname, p.oid;

SELECT n.oid, n.nspname AS schema_name,
       pg_catalog.pg_get_userbyid(n.nspowner) AS owner,
       CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE grantee.rolname END AS grantee,
       pg_catalog.pg_get_userbyid(a.grantor) AS grantor,
       a.privilege_type, a.is_grantable
FROM pg_catalog.pg_namespace n CROSS JOIN LATERAL pg_catalog.aclexplode(
    COALESCE(n.nspacl, pg_catalog.acldefault('n', n.nspowner))) a
LEFT JOIN pg_catalog.pg_roles grantee ON grantee.oid = a.grantee
WHERE EXISTS (SELECT 1 FROM pg_catalog.pg_proc p WHERE p.pronamespace = n.oid
    AND (p.prosecdef OR p.oid = ANY (?::pg_catalog.oid[])))
ORDER BY n.nspname, a.grantee, a.grantor, a.privilege_type;

WITH RECURSIVE paths AS (
    SELECT DISTINCT o.oid AS origin, o.oid AS reached, ARRAY[o.oid] AS visited,
           ARRAY[o.rolname::pg_catalog.text] AS names,
           true AS inherit_path, true AS set_path
    FROM pg_catalog.pg_roles o
    WHERE o.rolname IN ('dxos_wf25_runtime', 'dxos_wf25_migrator') OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_proc p WHERE p.proowner = o.oid
        AND (p.prosecdef OR p.oid = ANY (?::pg_catalog.oid[])))
    UNION ALL
    SELECT p.origin, m.roleid, p.visited || m.roleid, p.names || r.rolname::pg_catalog.text,
           p.inherit_path AND m.inherit_option, p.set_path AND m.set_option
    FROM paths p JOIN pg_catalog.pg_auth_members m ON m.member = p.reached
    JOIN pg_catalog.pg_roles r ON r.oid = m.roleid
    WHERE NOT m.roleid = ANY (p.visited)
)
SELECT pg_catalog.pg_get_userbyid(origin) AS origin_role,
       pg_catalog.pg_get_userbyid(reached) AS reached_role,
       pg_catalog.array_to_json(names)::pg_catalog.text AS membership_path,
       inherit_path, set_path,
       pg_catalog.pg_has_role(origin, reached, 'USAGE') AS effective_inherit,
       pg_catalog.pg_has_role(origin, reached, 'SET') AS effective_set,
       pg_catalog.pg_has_role(origin, reached, 'MEMBER WITH ADMIN OPTION') AS effective_admin
FROM paths WHERE origin <> reached ORDER BY names;

WITH RECURSIVE selected AS (
    SELECT p.oid FROM pg_catalog.pg_proc p
    WHERE p.prosecdef OR p.oid = ANY (?::pg_catalog.oid[])
)
SELECT 'outgoing' AS direction, p.oid AS routine_oid,
       d.refclassid::pg_catalog.regclass::pg_catalog.text AS related_catalog,
       d.refobjid AS related_oid, d.refobjsubid AS related_sub_id, d.deptype AS dependency_type,
       (pg_catalog.pg_identify_object(d.refclassid, d.refobjid, d.refobjsubid)).identity AS related_identity
FROM selected p JOIN pg_catalog.pg_depend d
    ON d.classid = 'pg_catalog.pg_proc'::pg_catalog.regclass AND d.objid = p.oid
UNION ALL
SELECT 'incoming', p.oid, d.classid::pg_catalog.regclass::pg_catalog.text,
       d.objid, d.objsubid, d.deptype,
       (pg_catalog.pg_identify_object(d.classid, d.objid, d.objsubid)).identity
FROM selected p JOIN pg_catalog.pg_depend d
    ON d.refclassid = 'pg_catalog.pg_proc'::pg_catalog.regclass AND d.refobjid = p.oid
ORDER BY routine_oid, direction, related_catalog, related_oid, related_sub_id, dependency_type;

SELECT pg_catalog.to_regprocedure(?)::pg_catalog.oid AS helper_oid;

ROLLBACK;
