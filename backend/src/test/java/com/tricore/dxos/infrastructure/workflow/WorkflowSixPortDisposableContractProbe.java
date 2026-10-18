package com.tricore.dxos.infrastructure.workflow;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TEST-ONLY reduced six-function contract, rotating HMAC secrets and deterministic two-connection
 * ACL lock races. Never registered as a production bean. Not V5 DDL / complete Workflow Core parity.
 * Uses the exclusively marked disposable PostgreSQL 17 cluster (never 5432 / staging 55426).
 */
public final class WorkflowSixPortDisposableContractProbe {
    private static final Pattern URL = Pattern.compile(
            "^jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):([0-9]{4,5})/(wfproof_test_[a-z0-9_]{8,48})$");
    private static final String DOMAIN = "DXOS-WF-SQL-CAP-SIX-DEMO";
    private static final String ACTOR = "oidc:verified-demo-actor";
    private static final String RESOURCE = "resource:A";
    private static final String INSTANCE = "wf_six_test_1";
    private static final String DEFINITION = "demo:1";
    private static final String SIGNATURE = "(TEXT,TEXT,TEXT,TEXT,TEXT,BIGINT,BIGINT,BYTEA)";
    private static final String[] METHODS = {"wf_port_load_definition", "wf_port_load_instance",
            "wf_port_load_history", "wf_port_create_instance", "wf_port_commit_activation",
            "wf_port_commit_transition"};

    private WorkflowSixPortDisposableContractProbe() {}

    public static void runFromEnvironment() throws Exception {
        if (!"ONLY_DISPOSABLE_PG17".equals(System.getenv("WORKFLOW_SQL_PROOF_DISPOSABLE_ACK"))) {
            throw new IllegalArgumentException("Test requires explicit disposable PG17 acknowledgement");
        }
        String jdbc = System.getenv("WORKFLOW_SQL_PROOF_DISPOSABLE_URL");
        Matcher parsed = URL.matcher(jdbc == null ? "" : jdbc);
        if (!parsed.matches()) throw new IllegalArgumentException("Refusing non-disposable JDBC URL");
        int port = Integer.parseInt(parsed.group(1));
        if (port == 5432 || port == 55426 || port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Forbidden default/staging port");
        }
        String adminLogin = required("WORKFLOW_SQL_PROOF_ADMIN_USER");
        String adminPassword = required("WORKFLOW_SQL_PROOF_ADMIN_PASSWORD");
        try (Connection admin = DriverManager.getConnection(jdbc, adminLogin, adminPassword)) {
            verifyDatabase(admin, parsed.group(2));
            String tag = hex(5);
            String schema = "wf6_data_" + tag;
            String crypto = "wf6_crypto_" + tag;
            String owner = "wf6_owner_" + tag;
            String runtime = "wf6_runtime_" + tag;
            String policy = "wf6_policy_" + tag;
            String runtimePassword = hex(24);
            String policyPassword = hex(24);
            byte[] keyA = randomKey();
            byte[] keyB = randomKey();
            boolean created = false;
            try {
                admin.setAutoCommit(false);
                try (Statement s = admin.createStatement()) {
                    s.execute("CREATE ROLE " + owner + " NOLOGIN NOINHERIT");
                    s.execute("CREATE ROLE " + runtime + " LOGIN NOINHERIT PASSWORD '" + runtimePassword + "'");
                    s.execute("CREATE ROLE " + policy + " LOGIN NOINHERIT PASSWORD '" + policyPassword + "'");
                    s.execute("CREATE SCHEMA " + schema + " AUTHORIZATION " + owner);
                    s.execute("CREATE SCHEMA " + crypto);
                    s.execute("CREATE EXTENSION pgcrypto WITH SCHEMA " + crypto);
                    s.execute("REVOKE ALL ON SCHEMA " + crypto + " FROM PUBLIC");
                    s.execute("GRANT USAGE ON SCHEMA " + crypto + " TO " + owner);
                    s.execute("SET ROLE " + owner);
                    s.execute(createTables(schema));
                    for (String ddl : tableDdl(schema)) s.execute(ddl);
                    seed(admin, schema, keyA);
                    s.execute(guardFunction(schema, crypto));
                    for (String ddl : runtimeFunctions(schema)) s.execute(ddl);
                    s.execute(policyFunction(schema, "wf_policy_revoke", false));
                    s.execute(policyFunction(schema, "wf_policy_grant", true));
                    s.execute("REVOKE ALL ON ALL FUNCTIONS IN SCHEMA " + schema + " FROM PUBLIC");
                    s.execute("RESET ROLE");
                    s.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + runtime + ", " + policy);
                    for (String method : METHODS) {
                        s.execute("GRANT EXECUTE ON FUNCTION " + schema + "." + method
                                + SIGNATURE + " TO " + runtime);
                    }
                    s.execute("GRANT EXECUTE ON FUNCTION " + schema
                            + ".wf_policy_revoke(TEXT) TO " + policy);
                    s.execute("GRANT EXECUTE ON FUNCTION " + schema
                            + ".wf_policy_grant(TEXT) TO " + policy);
                }
                admin.commit();
                created = true;
                System.out.println("WF6_FIXTURE_INSTALLED_ON_DISPOSABLE_ONLY");

                verifyRoleDenials(jdbc, runtime, runtimePassword, policy, policyPassword, schema);
                verifySixPortFunctions(jdbc, runtime, runtimePassword, schema, keyA);
                verifyKeyRotation(admin, jdbc, runtime, runtimePassword, schema, keyA, keyB);
                verifyConcurrency(jdbc, runtime, runtimePassword, policy, policyPassword, schema, keyB);
                System.out.println("WF6_DISPOSABLE_SIX_PORT_KEY_ROTATION_TWO_CONNECTION_RACES_PASS");
            } catch (Exception | AssertionError error) {
                if (!created) {
                    try { admin.rollback(); } catch (SQLException ignored) {}
                }
                throw error;
            } finally {
                Arrays.fill(keyA, (byte) 0);
                Arrays.fill(keyB, (byte) 0);
                try {
                    admin.setAutoCommit(true);
                    try (Statement s = admin.createStatement()) {
                        s.execute("RESET ROLE");
                        s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                        if (created) s.execute("DROP EXTENSION IF EXISTS pgcrypto");
                        s.execute("DROP SCHEMA IF EXISTS " + crypto + " CASCADE");
                        s.execute("DROP ROLE IF EXISTS " + runtime);
                        s.execute("DROP ROLE IF EXISTS " + policy);
                        s.execute("DROP ROLE IF EXISTS " + owner);
                    }
                } catch (SQLException cleanupFailure) {
                    throw new IllegalStateException("Disposable PG17 cleanup failed", cleanupFailure);
                }
            }
        }
    }

    private static void verifyDatabase(Connection admin, String database) throws SQLException {
        try (Statement s = admin.createStatement(); ResultSet rows = s.executeQuery(
                "SELECT current_database(), current_setting('server_version_num'), "
                        + "current_setting('dxos.wfproof_disposable', true), "
                        + "(SELECT rolsuper FROM pg_catalog.pg_roles WHERE rolname=current_user), "
                        + "(SELECT count(*) FROM pg_catalog.pg_extension WHERE extname='pgcrypto')")) {
            if (!rows.next() || !database.equals(rows.getString(1))
                    || Integer.parseInt(rows.getString(2))/10000 != 17
                    || !"on".equals(rows.getString(3))
                    || !rows.getBoolean(4) || rows.getInt(5)!=0) {
                throw new IllegalArgumentException("Only fresh marked disposable PostgreSQL 17 superuser DB");
            }
        }
    }

    private static String createTables(String s) {
        return "CREATE TABLE " + s + ".keys(kid TEXT PRIMARY KEY, secret BYTEA NOT NULL, "
                + "state TEXT NOT NULL CHECK(state IN ('ACTIVE','VERIFY_ONLY','REVOKED')))";
    }

    private static String[] tableDdl(String s) {
        return new String[]{
            "CREATE TABLE " + s + ".guards(tenant TEXT PRIMARY KEY, revision BIGINT NOT NULL)",
            "CREATE TABLE " + s + ".resources(id TEXT PRIMARY KEY, tenant TEXT NOT NULL REFERENCES " + s + ".guards(tenant))",
            "CREATE TABLE " + s + ".acl(tenant TEXT NOT NULL REFERENCES " + s
                    + ".guards(tenant), actor TEXT NOT NULL, action TEXT NOT NULL, allowed BOOLEAN NOT NULL, "
                    + "PRIMARY KEY(tenant,actor,action))",
            "CREATE TABLE " + s + ".definitions(id TEXT NOT NULL, version BIGINT NOT NULL, "
                    + "initial_state TEXT NOT NULL, PRIMARY KEY(id,version))",
            "CREATE TABLE " + s + ".instances(id TEXT PRIMARY KEY, tenant TEXT NOT NULL REFERENCES "
                    + s + ".guards(tenant), definition TEXT NOT NULL, state TEXT NOT NULL, "
                    + "runtime_version BIGINT NOT NULL, lifecycle TEXT NOT NULL)",
            "CREATE TABLE " + s + ".history(instance TEXT REFERENCES " + s
                    + ".instances(id), resulting_version BIGINT NOT NULL, actor TEXT NOT NULL, "
                    + "PRIMARY KEY(instance,resulting_version))"
        };
    }

    private static void seed(Connection c, String s, byte[] key) throws SQLException {
        update(c, "INSERT INTO " + s + ".keys(kid,secret,state) VALUES ('key-a',?,'ACTIVE')", key);
        update(c, "INSERT INTO " + s + ".guards VALUES ('tenant-a',0)");
        update(c, "INSERT INTO " + s + ".resources VALUES (?, 'tenant-a')", RESOURCE);
        update(c, "INSERT INTO " + s + ".definitions VALUES ('demo',1,'START')");
        for (String action : new String[]{"CREATE","READ","ACTIVATE","TRANSITION"}) {
            update(c, "INSERT INTO " + s + ".acl VALUES ('tenant-a',?,?,true)", ACTOR,action);
        }
    }

    private static String guardFunction(String s, String crypto) {
        return """
            CREATE FUNCTION %s.checked_scope(
                actor TEXT, port_action TEXT, target TEXT, payload TEXT,
                kid TEXT, nonce TEXT, issued BIGINT, expires BIGINT, signature BYTEA)
            RETURNS TEXT LANGUAGE plpgsql SECURITY DEFINER
            SET search_path = pg_catalog, pg_temp AS $wf6$
            DECLARE
                items TEXT[];
                part TEXT;
                encoded BYTEA := pg_catalog.int4send(11);
                the_key BYTEA;
                mac BYTEA;
                k_state TEXT;
                t TEXT;
                guarded TEXT;
                acl_action TEXT;
                now_ms BIGINT;
            BEGIN
                IF actor IS NULL OR target IS NULL OR payload IS NULL OR kid IS NULL
                     OR nonce IS NULL OR signature IS NULL OR issued IS NULL OR expires IS NULL THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                now_ms := (EXTRACT(EPOCH FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
                IF issued > now_ms+5000 OR issued < now_ms-60000
                    OR expires <= now_ms OR expires > issued+60000 THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                items := ARRAY['DXOS-WF-SQL-CAP-SIX-DEMO',actor,port_action,target,payload,kid,
                    pg_catalog.pg_backend_pid()::TEXT,pg_catalog.pg_current_xact_id()::TEXT,
                    nonce,issued::TEXT,expires::TEXT];
                FOREACH part IN ARRAY items LOOP
                    IF pg_catalog.octet_length(pg_catalog.convert_to(part,'UTF8')) > 4096 THEN
                        RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                    END IF;
                    encoded := encoded
                        || pg_catalog.int4send(pg_catalog.octet_length(pg_catalog.convert_to(part,'UTF8')))
                        || pg_catalog.convert_to(part,'UTF8');
                END LOOP;
                SELECT secret,state INTO the_key,k_state FROM %s.keys WHERE keys.kid=checked_scope.kid;
                IF k_state NOT IN ('ACTIVE','VERIFY_ONLY') OR the_key IS NULL THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                mac := %s.hmac(encoded,the_key,'sha256');
                -- TEST ONLY: PostgreSQL BYTEA equality is NOT audited constant-time.
                IF mac IS DISTINCT FROM signature THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;

                IF port_action IN ('LOAD_DEFINITION','CREATE_INSTANCE') THEN
                    acl_action := 'CREATE';
                    SELECT tenant INTO t FROM %s.resources WHERE id=target;
                ELSIF port_action = 'LOAD_HISTORY' OR port_action = 'LOAD_INSTANCE' THEN
                    acl_action := 'READ';
                    SELECT tenant INTO t FROM %s.instances WHERE id=target;
                ELSIF port_action = 'COMMIT_ACTIVATION' THEN
                    acl_action := 'ACTIVATE';
                    SELECT tenant INTO t FROM %s.instances WHERE id=target;
                ELSIF port_action = 'COMMIT_TRANSITION' THEN
                    acl_action := 'TRANSITION';
                    SELECT tenant INTO t FROM %s.instances WHERE id=target;
                ELSE
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                IF t IS NULL THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                SELECT tenant INTO guarded FROM %s.guards WHERE tenant=t FOR SHARE;
                IF guarded IS NULL THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                now_ms := (EXTRACT(EPOCH FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
                IF expires <= now_ms THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                IF NOT EXISTS (SELECT 1 FROM %s.acl a WHERE a.tenant=t
                             AND a.actor=checked_scope.actor AND a.action=acl_action AND a.allowed) THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                RETURN t;
            END $wf6$
            """.formatted(s,s,crypto,s,s,s,s,s,s);
    }

    private static String[] runtimeFunctions(String s) {
        String params = "(actor TEXT, target TEXT, payload TEXT, kid TEXT, nonce TEXT,"
                + " issued BIGINT, expires BIGINT, signature BYTEA)";
        String[] result = new String[6];
        String[] names = METHODS;
        String[] operations={"LOAD_DEFINITION","LOAD_INSTANCE","LOAD_HISTORY",
                "CREATE_INSTANCE","COMMIT_ACTIVATION","COMMIT_TRANSITION"};
        String[] actions = {
            "IF payload <> 'demo:1' THEN RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;"
                + " SELECT initial_state INTO output FROM " + s + ".definitions WHERE id='demo' AND version=1;"
                + " IF output IS NULL THEN RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;",
            "SELECT state INTO output FROM " + s + ".instances WHERE id=target;"
                + " IF output IS NULL THEN RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;",
            "SELECT COUNT(*)::TEXT INTO output FROM " + s + ".history WHERE instance=target;",
            "IF payload !~ '^wf6_created_[0-9a-z_]+$' THEN"
                + " RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;"
                + " INSERT INTO " + s + ".instances(id,tenant,definition,state,runtime_version,lifecycle)"
                + " VALUES (payload,tenant,'demo','START',0,'NOT_STARTED');"
                + " output := payload;",
            "IF payload <> '0' THEN RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;"
                + " UPDATE " + s + ".instances SET lifecycle='ACTIVE' WHERE id=target"
                + " AND runtime_version=0 AND lifecycle='NOT_STARTED';"
                + " IF NOT FOUND THEN RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;"
                + " output := 'ACTIVE';",
            "IF payload <> '0:COMPLETE' THEN RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;"
                + " UPDATE " + s + ".instances SET state='DONE',runtime_version=1,lifecycle='COMPLETED'"
                + " WHERE id=target AND runtime_version=0 AND lifecycle='ACTIVE';"
                + " IF NOT FOUND THEN RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;"
                + " INSERT INTO " + s + ".history VALUES (target,1,actor); output := 'DONE';"
        };
        for(int i=0;i<names.length;i++) {
            result[i]="CREATE FUNCTION "+s+"."+names[i]+params
                    +" RETURNS TEXT LANGUAGE plpgsql SECURITY DEFINER"
                    +" SET search_path=pg_catalog,pg_temp AS $wf6$"
                    +" DECLARE tenant TEXT; output TEXT;"
                    +" BEGIN SELECT "+s+".checked_scope(actor,'"+operations[i]
                    +"',target,payload,kid,nonce,issued,expires,signature) INTO tenant;"
                    +actions[i]+" RETURN output; END $wf6$";
        }
        return result;
    }

    private static String policyFunction(String s, String name, boolean enabled) {
        return "CREATE FUNCTION " + s + "." + name + "(action TEXT)"
                + " RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER "
                + "SET search_path=pg_catalog,pg_temp AS $policy$"
                + " BEGIN IF action NOT IN ('READ','TRANSITION','CREATE','ACTIVATE') THEN"
                + " RAISE EXCEPTION 'denied' USING ERRCODE='42501'; END IF;"
                + " PERFORM 1 FROM " + s + ".guards WHERE tenant='tenant-a' FOR UPDATE;"
                + " UPDATE " + s + ".acl SET allowed=" + enabled
                + " WHERE tenant='tenant-a' AND actor='" + ACTOR + "' AND acl.action=" + name + ".action;"
                + " UPDATE " + s + ".guards SET revision=revision+1 WHERE tenant='tenant-a';"
                + " END $policy$";
    }

    private static void verifyRoleDenials(String url, String runtime, String pwd, String policy,
                                         String policyPwd, String s) throws SQLException {
        for (String sql : new String[] {
                "SELECT * FROM " + s + ".keys",
                "SELECT * FROM " + s + ".instances",
                "UPDATE " + s + ".instances SET state='BYPASS' WHERE id='" + INSTANCE + "'",
                "DELETE FROM " + s + ".acl",
                "CREATE TABLE " + s + ".bypass(x INTEGER)",
                "SELECT " + s + ".checked_scope('actor','READ','target','-','key-a','nonce',0,1,decode('aa','hex'))",
                "SELECT " + s + ".wf_policy_revoke('READ')"
        }) expectSqlState(url,runtime,pwd,sql,"42501");
        expectSqlState(url,policy,policyPwd,"SELECT * FROM " + s + ".acl","42501");
        System.out.println("WF6_ROLES_NEGATIVE_SQL_PASS");
    }

    private static void verifySixPortFunctions(String url, String user, String pwd,
                                               String s, byte[] key) throws Exception {
        callSigned(url,user,pwd,s,METHODS[0],RESOURCE,DEFINITION,"key-a",key,"START");
        String id="wf6_created_"+hex(5);
        callSigned(url,user,pwd,s,METHODS[3],RESOURCE,id,"key-a",key,id);
        callSigned(url,user,pwd,s,METHODS[1],id,"","key-a",key,"START");
        callSigned(url,user,pwd,s,METHODS[2],id,"","key-a",key,"0");
        callSigned(url,user,pwd,s,METHODS[4],id,"0","key-a",key,"ACTIVE");
        callSigned(url,user,pwd,s,METHODS[5],id,"0:COMPLETE","key-a",key,"DONE");
        callSigned(url,user,pwd,s,METHODS[2],id,"","key-a",key,"1");
        // Signing a LOAD_INSTANCE capability must not authorize a HISTORY function.
        try(Connection c=DriverManager.getConnection(url,user,pwd)) {
            c.setAutoCommit(false);
            Proof proof = sign(c,METHODS[1],id,"","key-a",key);
            expectRejected(c,s,METHODS[2],id,"",proof);
        }
        // Previously completed transition is not replayable even with freshly signed proof.
        try(Connection c=DriverManager.getConnection(url,user,pwd)) {
            c.setAutoCommit(false);
            Proof proof=sign(c,METHODS[5],id,"0:COMPLETE","key-a",key);
            expectRejected(c,s,METHODS[5],id,"0:COMPLETE",proof);
        }
        System.out.println("WF6_ALL_SIX_SQL_ENTRYPOINTS_PASS_WITH_LIMITED_SEMANTICS");
    }

    private static void verifyKeyRotation(Connection admin, String url, String user,
                                          String pwd,String s,byte[] keyA,byte[] keyB) throws Exception {
        try(Connection old=DriverManager.getConnection(url,user,pwd)) {
            old.setAutoCommit(false);
            Proof existing=sign(old,METHODS[0],RESOURCE,DEFINITION,"key-a",keyA);
            update(admin,"UPDATE "+s+".keys SET state='VERIFY_ONLY' WHERE kid='key-a'");
            update(admin,"INSERT INTO "+s+".keys(kid,secret,state) VALUES('key-b',?,'ACTIVE')",keyB);
            admin.commit();
            assertResult(old,s,METHODS[0],RESOURCE,DEFINITION,existing,"START");
            old.commit();
        }
        callSigned(url,user,pwd,s,METHODS[0],RESOURCE,DEFINITION,"key-b",keyB,"START");
        try(Connection old=DriverManager.getConnection(url,user,pwd)) {
            old.setAutoCommit(false);
            Proof existing=sign(old,METHODS[0],RESOURCE,DEFINITION,"key-a",keyA);
            update(admin,"UPDATE "+s+".keys SET state='REVOKED' WHERE kid='key-a'");
            admin.commit();
            expectRejected(old,s,METHODS[0],RESOURCE,DEFINITION,existing);
        }
        // Expiration is verified independently from a valid MAC and transaction binding.
        try(Connection c=DriverManager.getConnection(url,user,pwd)) {
            c.setAutoCommit(false);
            Proof expired=signAt(c,METHODS[0],RESOURCE,DEFINITION,"key-b",keyB,
                    System.currentTimeMillis()-90_000,System.currentTimeMillis()-1);
            expectRejected(c,s,METHODS[0],RESOURCE,DEFINITION,expired);
        }
        // The DB login cannot SELECT either active or revoked secret material.
        expectSqlState(url,user,pwd,"SELECT secret FROM "+s+".keys","42501");
        System.out.println("WF6_KEY_ROTATION_VERIFY_ONLY_REVOKED_EXPIRED_PASS");
    }

    private static void verifyConcurrency(String url, String user, String password,
                                          String policy, String policyPassword, String s, byte[] keyB)
            throws Exception {
        String id="wf6_created_"+hex(5);
        callSigned(url,user,password,s,METHODS[3],RESOURCE,id,"key-b",keyB,id);
        callSigned(url,user,password,s,METHODS[4],id,"0","key-b",keyB,"ACTIVE");
        try (Connection writer=DriverManager.getConnection(url,user,password);
             Connection revoker=DriverManager.getConnection(url,policy,policyPassword)) {
            writer.setAutoCommit(false);
            revoker.setAutoCommit(false);
            Proof proof=sign(writer,METHODS[5],id,"0:COMPLETE","key-b",keyB);
            assertResult(writer,s,METHODS[5],id,"0:COMPLETE",proof,"DONE"); // holds guard FOR SHARE
            // Deterministic lock-timeout demonstrates FOR UPDATE cannot pass until writer finishes.
            setLockTimeout(revoker,200);
            expectPolicyLockTimeout(revoker,s,"wf_policy_revoke","TRANSITION");
            writer.commit();
            policyCall(revoker,s,"wf_policy_revoke","TRANSITION");
            revoker.commit();
        }
        // Revocation committed after writer's COMMIT: history is consistent and new operations deny.
        callSigned(url,user,password,s,METHODS[2],id,"","key-b",keyB,"1");
        try(Connection c=DriverManager.getConnection(url,user,password)) {
            c.setAutoCommit(false);
            Proof p=sign(c,METHODS[5],id,"0:COMPLETE","key-b",keyB);
            expectRejected(c,s,METHODS[5],id,"0:COMPLETE",p);
        }
        // Revoke-first: policy holds the tenant guard FOR UPDATE before READ ACL revocation commits.
        try(Connection revoker=DriverManager.getConnection(url,policy,policyPassword);
            Connection reader=DriverManager.getConnection(url,user,password)) {
            revoker.setAutoCommit(false);
            reader.setAutoCommit(false);
            policyCall(revoker,s,"wf_policy_revoke","READ");
            setLockTimeout(reader,200);
            Proof p=sign(reader,METHODS[2],id,"","key-b",keyB);
            expectSqlLockTimeout(reader,s,METHODS[2],id,"",p);
            revoker.commit();
        }
        try(Connection reader=DriverManager.getConnection(url,user,password)) {
            reader.setAutoCommit(false);
            Proof p=sign(reader,METHODS[2],id,"","key-b",keyB);
            expectRejected(reader,s,METHODS[2],id,"",p);
        }
        System.out.println("WF6_TWO_PHYSICAL_CONNECTION_REVOKE_FIRST_WRITE_FIRST_PASS");
    }

    private static void setLockTimeout(Connection c, int ms) throws SQLException {
        try(Statement s=c.createStatement()) {s.execute("SET LOCAL lock_timeout = '"+ms+"ms'");}
    }

    private static void expectPolicyLockTimeout(Connection c,String s,String func,String action) throws SQLException {
        try {policyCall(c,s,func,action);throw new AssertionError("Policy did not block on guard");}
        catch(SQLException failure) {
            if(!"55P03".equals(failure.getSQLState())) throw new AssertionError("Wrong lock timeout state",failure);
            c.rollback();
        }
    }

    private static void expectSqlLockTimeout(Connection c,String s,String func,String target,
                                             String payload,Proof p) throws SQLException {
        try{invoke(c,s,func,target,payload,p);throw new AssertionError("READ bypassed held guard");}
        catch(SQLException failure) {
            if(!"55P03".equals(failure.getSQLState())) throw new AssertionError("Wrong lock state",failure);
            c.rollback();
        }
    }

    private static void policyCall(Connection c,String s,String action,String type) throws SQLException {
        try(PreparedStatement q=c.prepareStatement("SELECT "+s+"."+action+"(?)")){
            q.setString(1,type);q.execute();
        }
    }

    private static void callSigned(String url,String user,String pwd,String s,String operation,
                                   String target,String payload,String kid,byte[] key,String expected) throws Exception {
        try(Connection c=DriverManager.getConnection(url,user,pwd)){
            c.setAutoCommit(false);
            Proof p=sign(c,operation,target,payload,kid,key);
            assertResult(c,s,operation,target,payload,p,expected);
            c.commit();
        }
    }

    private static void assertResult(Connection c,String s,String operation,String target,String payload,
                                     Proof proof,String expected) throws SQLException {
        String result=invoke(c,s,operation,target,payload,proof);
        if(!expected.equals(result))throw new AssertionError("Unexpected "+operation+": "+result);
    }

    private static String invoke(Connection c,String s,String op,String target,String payload,Proof p)
            throws SQLException {
        try(PreparedStatement q=c.prepareStatement("SELECT "+s+"."+op+"(?,?,?,?,?,?,?,?)")) {
            q.setString(1,ACTOR);q.setString(2,target);q.setString(3,payload);
            q.setString(4,p.kid);q.setString(5,p.nonce);
            q.setLong(6,p.issued);q.setLong(7,p.expires);q.setBytes(8,p.mac);
            try(ResultSet rows=q.executeQuery()){
                if(!rows.next())throw new AssertionError("Missing SQL return from "+op);
                return rows.getString(1);
            }
        }
    }

    private static void expectRejected(Connection c,String s,String op,String target,String payload,Proof proof)
            throws SQLException {
        try{invoke(c,s,op,target,payload,proof);throw new AssertionError("Signed SQL misuse succeeded");}
        catch(SQLException denied){
            if(!"42501".equals(denied.getSQLState()))throw new AssertionError("Unexpected deny SQLSTATE",denied);
            c.rollback();
        }
    }

    private static void expectSqlState(String url,String user,String pwd,String sql,String state)
            throws SQLException {
        try(Connection c=DriverManager.getConnection(url,user,pwd);Statement q=c.createStatement()){
            try{q.execute(sql);throw new AssertionError("SQL role restriction bypassed: "+sql);}
            catch(SQLException denied) {
                if(!state.equals(denied.getSQLState()))throw new AssertionError("Wrong rejection SQLSTATE",denied);
            }
        }
    }

    private static Proof sign(Connection c,String op,String target,String payload,String kid,byte[] key)
            throws Exception {
        long now=System.currentTimeMillis();
        return signAt(c,op,target,payload,kid,key,now,now+30_000);
    }

    private static Proof signAt(Connection c,String op,String target,String payload,String kid,byte[] key,
                                long issued,long expires) throws Exception {
        String pid,xid;
        try(Statement q=c.createStatement();ResultSet rows=q.executeQuery(
                "SELECT pg_catalog.pg_backend_pid()::TEXT,pg_catalog.pg_current_xact_id()::TEXT")) {
            if(!rows.next())throw new AssertionError("Missing actual physical transaction");
            pid=rows.getString(1);xid=rows.getString(2);
        }
        String nonce=hex(12);
        byte[] input=canonical(DOMAIN,ACTOR,op,target,payload,kid,pid,xid,nonce,
                Long.toString(issued),Long.toString(expires));
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key,"HmacSHA256"));
        return new Proof(kid,nonce,issued,expires,mac.doFinal(input));
    }

    private static byte[] canonical(String... fields) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        DataOutputStream bytes=new DataOutputStream(out);
        bytes.writeInt(fields.length);
        for(String f:fields) {
            if(f==null||f.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid text field");
            var encoded=StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(f));
            if(encoded.remaining()>4096)throw new IllegalArgumentException("Field too large");
            bytes.writeInt(encoded.remaining());
            while(encoded.hasRemaining())bytes.writeByte(encoded.get());
        }
        return out.toByteArray();
    }

    private record Proof(String kid,String nonce,long issued,long expires,byte[] mac) {}

    private static byte[] randomKey() {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return bytes;
    }
    private static String hex(int n) {
        byte[] bytes=new byte[n];new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).toLowerCase(Locale.ROOT);
    }
    private static String required(String env) {
        String x=System.getenv(env);
        if(x==null||x.isBlank())throw new IllegalArgumentException("Missing "+env);
        return x;
    }
    private static void update(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++){
                if(args[i] instanceof byte[] b)p.setBytes(i+1,b);
                else p.setObject(i+1,args[i]);
            }
            p.executeUpdate();
        }
    }
}
