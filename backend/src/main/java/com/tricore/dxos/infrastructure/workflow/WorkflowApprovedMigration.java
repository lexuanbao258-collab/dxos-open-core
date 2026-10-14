package com.tricore.dxos.infrastructure.workflow;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.springframework.jdbc.datasource.AbstractDataSource;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Owner-approved V3/V4 only; no bootstrap, smoke, role provisioning or recovery writes. */
public final class WorkflowApprovedMigration {
    static final String S = WorkflowSmokeRunner.SCHEMA;
    static final String URL = WorkflowSchemaBootstrap.URL;
    static final String MANIFEST_SHA = "d5de8c9deb62590653f2fb19d184c8ad9298d3a5386c98cf2b4b50eefc4a3237";
    // Canonical catalog hashes are reviewed against the pinned bundle on PostgreSQL 17.11.
    static final Map<String,String> EXPECTED = Map.of(
        "relations","de8b4e77788fbc5e55eb06aafd4c07db72379cdb879e1b28b38850a75210026d",
        "columns","332416cecc8a978c9e757576c899bcd326216b758e7d62c71dcbc7054830b410",
        "constraints","7626a3512a9b9f8b71452192c0a5d999d340400ece51b6b3a2fdb4fa24707158",
        "indexes","03d2f373e4bd3b91b9e89b4e0a40ecff428f49edc66fd5d2343e2550ae38728f",
        "functions","719042b6f26423f5c740463b10eff1ed61a81e605beec12c808d35f81ff8da0f",
        "triggers","90a9231c206bfca78cf60259f1c14170b789dd52008982f7a4dd3c79dd3aaa33",
        "internal_triggers","a1a6ecaed976a23c0aa31c2e289fc1d5b9ece0652a9386e991d715169527af8e",
        "types","dec981be373c75e484fa75a38daf860bf1b01a30c3dd7e63d69201611613ecfc",
        "other_objects","4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945");
    private WorkflowApprovedMigration() {}

    @FunctionalInterface interface Migration { void execute(Flyway flyway) throws Exception; }
    record History(String v3, String v4, String state, boolean ready, boolean anySuccess, boolean noRows) {}
    record Result(String status, boolean direct, boolean tls, History history, String inventory,
                  String seed, String audit, String outside, String phase, String failureCode, String sqlState) {
        int report(PrintStream out) {
            out.println("FLYWAY_EXECUTION="+status);
            out.println("DIRECT_CONNECTION="+(direct?"PASS":"FAIL"));
            out.println("TLS_VERIFIED="+(tls?"YES":"NO"));
            out.println("V3_STATUS="+(history==null?"NOT_RUN":history.v3()));
            out.println("V4_STATUS="+(history==null?"NOT_RUN":history.v4()));
            out.println("FLYWAY_HISTORY="+(history==null?"UNVERIFIED":history.state()));
            out.println("INVENTORY_CHECK="+inventory);
            out.println("SEED_CHECK="+seed);
            out.println("POSTCOMMIT_AUDIT="+audit);
            out.println("OTHER_CLOUD_WRITES="+outside);
            out.println("MIGRATION_PHASE="+phase);
            out.println("FAILURE_CODE="+failureCode);
            out.println("FAILURE_SQLSTATE="+sqlState);
            out.println("NEXT_APPROVAL_REQUIRED=YES");
            return status.equals("COMMITTED_PASS") && audit.equals("PASS") ? 0 : 1;
        }
    }

    static Properties properties(Map<String,String> env, boolean write) {
        require(S.equals(env.get("WORKFLOW_FLYWAY_APPROVAL")), "Explicit migration approval required");
        require(WorkflowSchemaBootstrap.HOST.equals(env.get("DB_HOST")) && "5432".equals(env.get("DB_PORT"))
                && "postgres".equals(env.get("DB_NAME")) && "postgres".equals(env.get("DB_USER")), "Direct identity mismatch");
        for(String flag:List.of("WORKFLOW_DEV_LOCAL_DISPOSABLE","WF25_DISPOSABLE_TEST","WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE"))
            require(!env.containsKey(flag), "Disposable flags rejected");
        require(Runtime.version().feature()==21, "JDK 21 required");
        Properties p=WorkflowLiveGuard.connectionProperties(env);
        p.setProperty("currentSchema",S+",pg_catalog,pg_temp");
        p.setProperty("readOnly",write?"false":"true");
        p.setProperty("options",(write?"":"-c default_transaction_read_only=on ")+"-c lock_timeout=5s -c statement_timeout=30s");
        p.setProperty("ApplicationName","dxos-wf25-approved-v3-v4");
        return p;
    }

    static void bundle(Path path) throws Exception {
        require(EXPECTED.size()==9,"Reviewed inventory is required");
        try(var stream=WorkflowApprovedMigration.class.getResourceAsStream("/workflow-event-trigger-manifest.json")) {
            require(stream!=null && HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(stream.readAllBytes())).equals(MANIFEST_SHA), "Manifest bytes changed");
        }
        WorkflowEventTriggerGuard.verifyBundle(path);
    }

    static Result run(Map<String,String> env, Path path, WorkflowReadOnlyPreflight.Connector connector,
                      WorkflowEventTriggerGuard guard, Migration migration) {
        String phase="validation", status="NOT_EXECUTED", inv="NOT_RUN", seed="NOT_RUN", audit="NOT_RUN", outside="NONE";
        String failureCode="NONE",sqlState="NONE";
        boolean direct=false,tls=false,handedOff=false,ack=false,unknown=false;
        Map<String,String> before=null;
        WorkflowSchemaBootstrap.Audit schema=null;
        History history=null;
        try {
            Properties read=properties(env,false), write=properties(env,true);
            phase="bundle_verification";
            bundle(path);
            phase="connect_verify_full";
            try(Connection c=connector.connect(URL,read)) {
                direct=true; tls=true;
                phase="begin_read_only";
                WorkflowSchemaBootstrap.beginReadOnly(c);
                try {
                    phase="session_identity_tls_version";
                    WorkflowSchemaBootstrap.session(c,true);
                    phase="read_only_search_path";
                    require(bool(c,"SELECT pg_catalog.replace(pg_catalog.current_setting('search_path'),' ','')="
                            +"'dxos_wf25_smoke_01,pg_catalog,pg_temp'"),"Approved read-only search path required");
                    phase="event_catalog_guard";
                    guard.verifyReadOnly(c,S);
                    phase="permissions_precheck";
                    permissions(c);
                    phase="schema_presence";
                    schema=WorkflowSchemaBootstrap.audit(c);
                    require(schema!=null,"Approved schema required");
                    phase="schema_owner";
                    require("postgres".equals(schema.owner()),"Approved schema owner required");
                    phase="schema_acl";
                    require(schema.privateAccess(),"Private schema ACL required");
                    phase="history_absent";
                    require(schema.historyAbsent(),"History must be absent");
                    phase="schema_empty";
                    require(schema.empty(),"Schema must be completely empty");
                    phase="catalog_snapshot";
                    before=snapshot(c);
                } finally { c.rollback(); }
            }
            final var baseline=before;
            final long oid=schema.oid();
            var source=new AbstractDataSource() {
                @Override public Connection getConnection() throws SQLException {
                    Connection c=connector.connect(URL,write);
                    try {
                        WorkflowSchemaBootstrap.beginReadOnly(c);
                        session(c,guard);
                        require(bool(c,"SELECT pg_catalog.current_setting('default_transaction_read_only')='off'"),"Writer default read-only rejected");
                        var actual=WorkflowSchemaBootstrap.audit(c);
                        require(actual!=null && actual.oid()==oid && "postgres".equals(actual.owner()) && actual.privateAccess(),
                                "Schema identity or ACL changed");
                        require(baseline.equals(snapshot(c)), "Outside catalog drift");
                        bundle(path);
                        c.rollback(); c.setAutoCommit(true); c.setReadOnly(false);
                        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                        return c;
                    } catch(Exception failure) {
                        try { c.close(); } catch(Exception ignored) {}
                        throw new SQLException("Approved migration connection rejected",failure);
                    }
                }
                @Override public Connection getConnection(String user,String password) throws SQLException {
                    throw new SQLException("Alternate login rejected");
                }
            };
            Flyway flyway=WorkflowMigrations.configure(source,S,"filesystem:"+path.toAbsolutePath())
                    .target("4").group(false).connectRetries(0).lockRetryCount(0).validateOnMigrate(true).loggers("slf4j").load();
            phase="flyway_pending_precheck";
            var info=flyway.info().all();
            require(info.length==2 && Arrays.stream(info).allMatch(i -> i.getState()==MigrationState.PENDING
                    && ((i.getVersion().getVersion().equals("3") && i.getScript().equals("V3__create_workflow_persistence.sql")
                        && Objects.equals(i.getResolvedChecksum(),-844876185))
                    || (i.getVersion().getVersion().equals("4") && i.getScript().equals("V4__seed_workflow_definitions.sql")
                        && Objects.equals(i.getResolvedChecksum(),1349884712)))), "Only pinned V3/V4 may be pending");
            bundle(path);
            phase="flyway_migrate_once";
            handedOff=true; outside="UNVERIFIED_POSTCOMMIT_AUDIT_NOT_RUN";
            migration.execute(flyway);
            ack=true;
        } catch(Exception failure) {
            failureCode=failureCode(phase,failure);
            sqlState=sqlState(failure);
            if(handedOff) unknown=!knownSqlFailure(failure);
        }
        if(handedOff) {
            status="COMMIT_UNKNOWN"; audit="FAIL";
            try(Connection fresh=connector.connect(URL,properties(env,false))) {
                WorkflowSchemaBootstrap.beginReadOnly(fresh);
                try {
                    session(fresh,guard);
                    history=history(fresh);
                    var observed=WorkflowSchemaBootstrap.audit(fresh);
                    var after=snapshot(fresh);
                    var changed=new TreeSet<String>();
                    for(String key:before.keySet()) if(!Objects.equals(before.get(key),after.get(key)))changed.add(key);
                    outside=changed.isEmpty()?"NONE":"CATALOG_DRIFT_"+String.join("_",changed);
                    boolean identity=observed!=null && observed.oid()==schema.oid()
                            && "postgres".equals(observed.owner()) && observed.privateAccess();
                    inv=EXPECTED.equals(inventory(fresh)) && bool(fresh,"""
                        SELECT NOT EXISTS(SELECT FROM pg_catalog.pg_rewrite w JOIN pg_catalog.pg_class c ON c.oid=w.ev_class
                                          WHERE c.relnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace)
                           AND NOT EXISTS(SELECT FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_class c ON c.oid=a.attrelid,
                                          LATERAL pg_catalog.aclexplode(a.attacl) grant_row
                                          WHERE c.relnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace
                                            AND grant_row.grantee<>c.relowner)
                        """)?"PASS":"FAIL";
                    seed=seed(fresh)?"PASS":"FAIL";
                    bundle(path);
                    audit=identity && outside.equals("NONE") && history.ready() && inv.equals("PASS") && seed.equals("PASS")
                            ?"PASS":"FAIL";
                    if(audit.equals("FAIL") && failureCode.equals("NONE"))failureCode="POSTCOMMIT_EXPECTATION_MISMATCH";
                    if(!unknown) {
                        if(ack && audit.equals("PASS"))status="COMMITTED_PASS";
                        else if(history.anySuccess())status="PARTIAL_COMMIT";
                        else if(history.noRows() && identity && outside.equals("NONE") && workflowObjectsAbsent(fresh))
                            status="FAILED_ROLLED_BACK";
                    }
                    phase="fresh_read_only_audit_"+audit.toLowerCase(Locale.ROOT);
                } finally { fresh.rollback(); }
            } catch(Exception failure) {
                phase="reconciliation_failed";
                if(failureCode.equals("NONE")) {failureCode="RECONCILIATION_FAILED";sqlState=sqlState(failure);}
            }
        }
        return new Result(status,direct,tls,history,inv,seed,audit,outside,phase,failureCode,sqlState);
    }

    private static String sqlState(Throwable failure) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable t=failure;t!=null && seen.add(t);t=t.getCause())
            if(t instanceof SQLException s && s.getSQLState()!=null && s.getSQLState().matches("[0-9A-Z]{5}"))return s.getSQLState();
        return "NONE";
    }

    private static String failureCode(String phase,Throwable failure) {
        if(phase.equals("connect_verify_full"))return sqlState(failure).startsWith("28")?"AUTHENTICATION_FAILED":"CONNECTION_OR_TLS_FAILED";
        return switch(phase) {
            case "validation" -> "CONFIGURATION_REJECTED";
            case "bundle_verification" -> "BUNDLE_REJECTED";
            case "begin_read_only" -> "READ_ONLY_TRANSACTION_REJECTED";
            case "session_identity_tls_version" -> "SESSION_IDENTITY_TLS_VERSION_REJECTED";
            case "read_only_search_path" -> "SEARCH_PATH_MISMATCH";
            case "event_catalog_guard" -> "EVENT_CATALOG_GUARD_REJECTED";
            case "permissions_precheck" -> "PERMISSIONS_REJECTED";
            case "schema_presence" -> "SCHEMA_ABSENT";
            case "schema_owner" -> "SCHEMA_OWNER_MISMATCH";
            case "schema_acl" -> "SCHEMA_ACL_REJECTED";
            case "history_absent" -> "FLYWAY_HISTORY_PRESENT";
            case "schema_empty" -> "SCHEMA_NOT_EMPTY";
            case "catalog_snapshot" -> "CATALOG_SNAPSHOT_FAILED";
            case "flyway_pending_precheck" -> "FLYWAY_PENDING_PRECHECK_FAILED";
            default -> "MIGRATION_EXECUTION_FAILED";
        };
    }

    private static boolean knownSqlFailure(Throwable failure) {
        boolean sql=false;
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        var pending=new ArrayDeque<Throwable>();
        pending.add(failure);
        while(!pending.isEmpty()) {
            Throwable t=pending.removeFirst();
            if(!seen.add(t))continue;
            if(t instanceof IOException || t instanceof SQLTimeoutException)return false;
            if(t instanceof SQLException s) {
                String state=s.getSQLState();
                if(state==null || state.startsWith("08") || state.startsWith("57P"))return false;
                sql=true;
                if(s.getNextException()!=null)pending.add(s.getNextException());
            }
            if(t.getCause()!=null)pending.add(t.getCause());
            pending.addAll(Arrays.asList(t.getSuppressed()));
        }
        return sql;
    }

    private static void session(Connection c,WorkflowEventTriggerGuard guard) throws SQLException {
        WorkflowSchemaBootstrap.session(c,true);
        guard.verifyReadOnly(c,S);
        permissions(c);
    }

    static void permissions(Connection c) throws SQLException {
        require(bool(c,"""
            SELECT pg_catalog.has_language_privilege(current_user,'plpgsql','USAGE')
              AND pg_catalog.has_schema_privilege(current_user,'dxos_wf25_smoke_01','USAGE')
              AND pg_catalog.has_schema_privilege(current_user,'dxos_wf25_smoke_01','CREATE')
              AND NOT EXISTS(SELECT FROM pg_catalog.pg_default_acl
                 WHERE defaclrole=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='postgres')
                 AND (defaclnamespace=0 OR defaclnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace))
              AND (SELECT count(*)=2 AND pg_catalog.bool_and(NOT rolcanlogin)
                   FROM pg_catalog.pg_roles WHERE rolname IN('dxos_wf25_migrator','dxos_wf25_runtime'))
            """),"Permissions, default ACL or custom roles rejected");
    }

    static History history(Connection c) throws SQLException {
        if(bool(c,"SELECT pg_catalog.to_regclass('dxos_wf25_smoke_01.flyway_schema_history') IS NULL"))
            return new History("ABSENT","ABSENT","ABSENT",false,false,true);
        String a="ABSENT",b="ABSENT"; boolean any=false,all=true;int rows=0;
        try(var st=c.createStatement();var r=st.executeQuery("""
            SELECT installed_rank,version,script,checksum,success,type,installed_by
            FROM dxos_wf25_smoke_01.flyway_schema_history ORDER BY installed_rank
            """)) {
            while(r.next()) {
                rows++;boolean success=r.getBoolean(5);any|=success;
                String v=r.getString(2);
                boolean pin=success && "SQL".equals(r.getString(6)) && "postgres".equals(r.getString(7))
                        && ("3".equals(v) && r.getInt(1)==1 && "V3__create_workflow_persistence.sql".equals(r.getString(3))
                                && Objects.equals(r.getObject(4),-844876185)
                        || "4".equals(v) && r.getInt(1)==2 && "V4__seed_workflow_definitions.sql".equals(r.getString(3))
                                && Objects.equals(r.getObject(4),1349884712));
                all&=pin;
                if("3".equals(v)) { if(!a.equals("ABSENT"))all=false; a=pin?"SUCCESS_PIN_MATCH":"MISMATCH"; }
                else if("4".equals(v)) { if(!b.equals("ABSENT"))all=false; b=pin?"SUCCESS_PIN_MATCH":"MISMATCH"; }
                else all=false;
            }
        }
        boolean ready=rows==2 && all && a.equals("SUCCESS_PIN_MATCH") && b.equals("SUCCESS_PIN_MATCH");
        String state=ready?"V3_V4_SUCCESS_CHECKSUM_MATCH":rows==0?"EMPTY":
                rows==1 && all && a.equals("SUCCESS_PIN_MATCH")?"V3_ONLY_SUCCESS":"MISMATCH";
        return new History(a,b,state,ready,any,rows==0);
    }

    static Map<String,String> inventory(Connection c) throws SQLException {
        String scope="'dxos_wf25_smoke_01'::pg_catalog.regnamespace";
        var q=new LinkedHashMap<String,String>();
        q.put("relations","SELECT c.relname,c.relkind,c.relpersistence,c.relrowsecurity,c.relforcerowsecurity,r.rolname AS owner,"
                +"COALESCE(c.relacl,pg_catalog.acldefault('r',c.relowner))::text AS acl FROM pg_catalog.pg_class c "
                +"JOIN pg_catalog.pg_roles r ON r.oid=c.relowner WHERE c.relnamespace="+scope);
        q.put("columns","SELECT c.relname,a.attname,a.attnum,pg_catalog.format_type(a.atttypid,a.atttypmod) AS type,"
                +"a.attnotnull,a.attidentity,a.attgenerated,pg_catalog.pg_get_expr(d.adbin,d.adrelid) AS expression "
                +"FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_class c ON c.oid=a.attrelid "
                +"LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum "
                +"WHERE c.relnamespace="+scope+" AND c.relkind='r' AND a.attnum>0 AND NOT a.attisdropped");
        q.put("constraints","SELECT c.relname,k.conname,k.contype,k.condeferrable,k.condeferred,k.convalidated,"
                +"pg_catalog.pg_get_constraintdef(k.oid) AS definition FROM pg_catalog.pg_constraint k "
                +"LEFT JOIN pg_catalog.pg_class c ON c.oid=k.conrelid WHERE k.connamespace="+scope);
        q.put("indexes","SELECT c.relname,pg_catalog.pg_get_indexdef(i.indexrelid) AS definition,i.indisvalid,i.indisready "
                +"FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class c ON c.oid=i.indexrelid WHERE c.relnamespace="+scope);
        q.put("functions","SELECT p.proname,pg_catalog.pg_get_function_identity_arguments(p.oid) AS args,"
                +"replace(pg_catalog.pg_get_functiondef(p.oid),chr(13)||chr(10),chr(10)) AS definition,"
                +"r.rolname AS owner,COALESCE(p.proacl,pg_catalog.acldefault('f',p.proowner))::text AS acl "
                +"FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_roles r ON r.oid=p.proowner WHERE p.pronamespace="+scope);
        q.put("triggers","SELECT c.relname,t.tgname,t.tgenabled,pg_catalog.pg_get_triggerdef(t.oid) AS definition "
                +"FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid "
                +"WHERE c.relnamespace="+scope+" AND NOT t.tgisinternal");
        q.put("internal_triggers","SELECT c.relname,k.conname,t.tgtype,t.tgenabled,t.tgdeferrable,t.tginitdeferred,p.proname "
                +"FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid "
                +"JOIN pg_catalog.pg_constraint k ON k.oid=t.tgconstraint JOIN pg_catalog.pg_proc p ON p.oid=t.tgfoid "
                +"WHERE c.relnamespace="+scope+" AND t.tgisinternal");
        q.put("types","SELECT t.typname,t.typtype,t.typcategory,r.rolname AS owner "
                +"FROM pg_catalog.pg_type t JOIN pg_catalog.pg_roles r ON r.oid=t.typowner WHERE t.typnamespace="+scope);
        q.put("other_objects","SELECT 'operator' AS kind,oprname AS name FROM pg_catalog.pg_operator WHERE oprnamespace="+scope
                +" UNION ALL SELECT 'collation',collname FROM pg_catalog.pg_collation WHERE collnamespace="+scope
                +" UNION ALL SELECT 'conversion',conname FROM pg_catalog.pg_conversion WHERE connamespace="+scope
                +" UNION ALL SELECT 'opclass',opcname FROM pg_catalog.pg_opclass WHERE opcnamespace="+scope
                +" UNION ALL SELECT 'opfamily',opfname FROM pg_catalog.pg_opfamily WHERE opfnamespace="+scope
                +" UNION ALL SELECT 'ts_config',cfgname FROM pg_catalog.pg_ts_config WHERE cfgnamespace="+scope
                +" UNION ALL SELECT 'ts_dict',dictname FROM pg_catalog.pg_ts_dict WHERE dictnamespace="+scope
                +" UNION ALL SELECT 'ts_parser',prsname FROM pg_catalog.pg_ts_parser WHERE prsnamespace="+scope
                +" UNION ALL SELECT 'ts_template',tmplname FROM pg_catalog.pg_ts_template WHERE tmplnamespace="+scope);
        String previous;
        try(var st=c.createStatement();var r=st.executeQuery("SELECT pg_catalog.current_setting('search_path')")) {
            require(r.next(),"Search path unavailable");previous=r.getString(1);
        }
        Map<String,String> result;
        try {
            inventorySearchPath(c,"pg_catalog,pg_temp");
            result=fingerprints(c,q,"");
        } catch(SQLException | RuntimeException failure) {
            try {inventorySearchPath(c,previous);}catch(SQLException restore){failure.addSuppressed(restore);}
            throw failure;
        }
        inventorySearchPath(c,previous);
        return result;
    }

    private static void inventorySearchPath(Connection c,String value) throws SQLException {
        // Deparsers depend on visibility; retain the reviewed qualified catalog fingerprints.
        try(var st=c.prepareStatement("SELECT pg_catalog.set_config('search_path',?,true)")) {
            st.setString(1,value);st.executeQuery().close();
        }
    }

    static Map<String,String> snapshot(Connection c) throws SQLException {
        String prefix="""
            WITH rels AS (
              SELECT oid FROM pg_catalog.pg_class WHERE relnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace
              UNION SELECT reltoastrelid FROM pg_catalog.pg_class WHERE relnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace AND reltoastrelid<>0
              UNION SELECT i.indexrelid FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class t ON t.reltoastrelid=i.indrelid
                    WHERE t.relnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace
            ), objects AS (
              SELECT 'pg_catalog.pg_class'::pg_catalog.regclass AS classid,oid AS objid FROM rels
              UNION SELECT 'pg_catalog.pg_proc'::pg_catalog.regclass,oid FROM pg_catalog.pg_proc WHERE pronamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace
              UNION SELECT 'pg_catalog.pg_type'::pg_catalog.regclass,oid FROM pg_catalog.pg_type WHERE typnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace
              UNION SELECT 'pg_catalog.pg_constraint'::pg_catalog.regclass,oid FROM pg_catalog.pg_constraint WHERE connamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace
              UNION SELECT 'pg_catalog.pg_trigger'::pg_catalog.regclass,oid FROM pg_catalog.pg_trigger WHERE tgrelid IN(SELECT oid FROM rels)
              UNION SELECT 'pg_catalog.pg_attrdef'::pg_catalog.regclass,oid FROM pg_catalog.pg_attrdef WHERE adrelid IN(SELECT oid FROM rels)
            )
            """;
        var q=new LinkedHashMap<String,String>();
        q.put("namespaces","SELECT * FROM pg_catalog.pg_namespace");
        q.put("roles","SELECT * FROM pg_catalog.pg_roles");
        q.put("memberships","SELECT * FROM pg_catalog.pg_auth_members");
        q.put("defaults","SELECT * FROM pg_catalog.pg_default_acl");
        q.put("database_acl","SELECT oid,datname,datdba,datacl,datallowconn,datconnlimit FROM pg_catalog.pg_database");
        q.put("relations","SELECT oid,relname,relnamespace,reltype,reloftype,relowner,relam,relpersistence,relkind,relchecks,relhasrules,relhastriggers,relrowsecurity,relforcerowsecurity,relispartition,relacl,reloptions,relpartbound FROM pg_catalog.pg_class WHERE oid NOT IN(SELECT oid FROM rels)");
        for(var entry:Map.of("routines","pg_proc","types","pg_type","constraints","pg_constraint","triggers","pg_trigger","expressions","pg_attrdef").entrySet())
            q.put(entry.getKey(),"SELECT * FROM pg_catalog."+entry.getValue()+" t WHERE NOT EXISTS(SELECT FROM objects o WHERE o.classid='pg_catalog."+entry.getValue()+"'::pg_catalog.regclass AND o.objid=t.oid)");
        q.put("attributes","SELECT * FROM pg_catalog.pg_attribute WHERE attrelid NOT IN(SELECT oid FROM rels)");
        q.put("events","SELECT * FROM pg_catalog.pg_event_trigger");
        q.put("dependencies","SELECT * FROM pg_catalog.pg_depend d WHERE NOT EXISTS(SELECT FROM objects o WHERE o.classid=d.classid AND o.objid=d.objid)");
        q.put("shared_dependencies","SELECT * FROM pg_catalog.pg_shdepend d WHERE NOT (dbid=(SELECT oid FROM pg_catalog.pg_database WHERE datname=pg_catalog.current_database()) AND EXISTS(SELECT FROM objects o WHERE o.classid=d.classid AND o.objid=d.objid))");
        q.put("indexes","SELECT * FROM pg_catalog.pg_index WHERE indexrelid NOT IN(SELECT oid FROM rels)");
        return fingerprints(c,q,prefix);
    }

    private static Map<String,String> fingerprints(Connection c,Map<String,String> queries,String prefix) throws SQLException {
        var hashes=new LinkedHashMap<String,String>();
        for(var q:queries.entrySet())try(var st=c.createStatement()) {
            st.setQueryTimeout(15);
            try(var r=st.executeQuery(prefix+" SELECT pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to("
                    +"COALESCE(pg_catalog.jsonb_agg(pg_catalog.to_jsonb(x) ORDER BY pg_catalog.to_jsonb(x)::text COLLATE pg_catalog.\"C\"),'[]'::jsonb)::text,'UTF8')),'hex') FROM ("+q.getValue()+") x")) {
                require(r.next(),"Catalog fingerprint missing");hashes.put(q.getKey(),r.getString(1));
            }
        }
        return hashes;
    }

    private static boolean seed(Connection c) throws SQLException {
        return definitionSeed(c)
            && bool(c,"SELECT NOT EXISTS(SELECT FROM dxos_wf25_smoke_01.workflow_instances) AND NOT EXISTS(SELECT FROM dxos_wf25_smoke_01.workflow_transition_history)");
    }

    static boolean definitionSeed(Connection c) throws SQLException {
        if(!bool(c,"SELECT pg_catalog.to_regclass('dxos_wf25_smoke_01.workflow_definitions') IS NOT NULL"))return false;
        return tuples(c,"SELECT definition_id,definition_version,initial_state FROM dxos_wf25_smoke_01.workflow_definitions",3)
                .equals(Set.of("document-review|1|DRAFT","incident-resolution|1|OPEN"))
            && tuples(c,"SELECT definition_id,definition_version,state_id FROM dxos_wf25_smoke_01.workflow_definition_states",3)
                .equals(Set.of("document-review|1|DRAFT","document-review|1|IN_REVIEW","document-review|1|APPROVED","document-review|1|REJECTED",
                        "incident-resolution|1|OPEN","incident-resolution|1|IN_PROGRESS","incident-resolution|1|RESOLVED"))
            && tuples(c,"SELECT definition_id,definition_version,transition_id,source_state,target_state FROM dxos_wf25_smoke_01.workflow_definition_transitions",5)
                .equals(Set.of("document-review|1|submit|DRAFT|IN_REVIEW","document-review|1|approve|IN_REVIEW|APPROVED","document-review|1|reject|IN_REVIEW|REJECTED",
                        "incident-resolution|1|start|OPEN|IN_PROGRESS","incident-resolution|1|resolve|IN_PROGRESS|RESOLVED"))
            && bool(c,"SELECT count(*)=2 AND count(DISTINCT provisioning_transaction)=1 AND min(provisioning_transaction)>0 FROM dxos_wf25_smoke_01.workflow_definitions");
    }
    private static Set<String> tuples(Connection c,String sql,int columns) throws SQLException {
        var set=new HashSet<String>();int count=0;
        try(var st=c.createStatement();var r=st.executeQuery(sql)) {
            while(r.next()) {count++;var values=new ArrayList<String>();for(int i=1;i<=columns;i++)values.add(r.getString(i));set.add(String.join("|",values));}
        }
        require(set.size()==count,"Duplicate seed rows");return set;
    }
    private static boolean workflowObjectsAbsent(Connection c) throws SQLException {
        return bool(c,"""
            SELECT NOT EXISTS(SELECT FROM pg_catalog.pg_class WHERE relnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace
                               AND relname NOT IN('flyway_schema_history','flyway_schema_history_pk','flyway_schema_history_s_idx'))
               AND NOT EXISTS(SELECT FROM pg_catalog.pg_proc WHERE pronamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace)
               AND NOT EXISTS(SELECT FROM pg_catalog.pg_type WHERE typnamespace='dxos_wf25_smoke_01'::pg_catalog.regnamespace
                               AND typname NOT IN('flyway_schema_history','_flyway_schema_history'))
            """) && inventory(c).get("other_objects").equals(EXPECTED.get("other_objects"));
    }
    private static boolean bool(Connection c,String sql) throws SQLException {
        try(var st=c.createStatement()) {st.setQueryTimeout(15);try(var r=st.executeQuery(sql)){return r.next() && r.getBoolean(1);}}
    }
    private static void require(boolean condition,String reason) {if(!condition)throw new IllegalArgumentException(reason);}
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("org.flywaydb")).setLevel(ch.qos.logback.classic.Level.OFF);
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("org.springframework.jdbc")).setLevel(ch.qos.logback.classic.Level.OFF);
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        Result result;
        if(args.length!=1)result=new Result("NOT_EXECUTED",false,false,null,"NOT_RUN","NOT_RUN","NOT_RUN","NONE","arguments","ARGUMENTS_REJECTED","NONE");
        else result=run(System.getenv(),Path.of(args[0]),DriverManager::getConnection,WorkflowEventTriggerGuard.REVIEWED,Flyway::migrate);
        System.exit(result.report(System.out));
    }
}
