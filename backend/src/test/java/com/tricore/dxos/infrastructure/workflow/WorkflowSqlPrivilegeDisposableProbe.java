package com.tricore.dxos.infrastructure.workflow;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TEST-ONLY SQL role + HMAC interoperability probe. Creates and cleans up random objects in
 * a PREEXISTING, EXPLICITLY MARKED, ISOLATED PostgreSQL 17 disposable database only.
 *
 * This is deliberately a minimal READ proof, NOT ADR-0024's full signed authorization
 * protocol, complete Workflow ACL SQL implementation, replay ledger or production verifier.
 * SQL BYTEA equality here is NOT claimed constant-time; requires security review.
 * Never point at staging (127.0.0.1:55426), shared Supabase or a default postgres DB.
 */
public final class WorkflowSqlPrivilegeDisposableProbe {
    private static final Pattern SAFE_URL = Pattern.compile(
            "^jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):([0-9]{4,5})/(wfproof_test_[a-z0-9_]{8,48})$");
    private static final String ACTOR = "oidc:approved-test-actor";
    private static final String ACTION = "READ";
    private static final String TARGET = "workflow:A";
    private static final String VALUE = "authorized-workflow-record";

    private WorkflowSqlPrivilegeDisposableProbe() {}

    public static void main(String[] ignored) throws Exception {
        runFromEnvironment();
    }

    public static void runFromEnvironment() throws Exception {
        if (!"ONLY_DISPOSABLE_PG17".equals(System.getenv("WORKFLOW_SQL_PROOF_DISPOSABLE_ACK"))) {
            throw new IllegalStateException("Disposable PG17 explicit opt-in required");
        }
        String url = System.getenv("WORKFLOW_SQL_PROOF_DISPOSABLE_URL");
        Matcher match = SAFE_URL.matcher(url == null ? "" : url);
        if (!match.matches()) throw new IllegalArgumentException("Dedicated loopback PG17 database URL required");
        int port = Integer.parseInt(match.group(1));
        if (port == 55426 || port == 5432 || port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Default/shared/staging port is not allowed");
        }
        String adminUser = required("WORKFLOW_SQL_PROOF_ADMIN_USER");
        String adminPassword = required("WORKFLOW_SQL_PROOF_ADMIN_PASSWORD");
        try (Connection admin = DriverManager.getConnection(url, adminUser, adminPassword)) {
            proveDisposable(admin, match.group(2));

            String suffix = randomHex(6);
            String owner = "wfproof_owner_" + suffix;
            String runtimeRole = "wfproof_runtime_" + suffix;
            String protectedSchema = "wfproof_data_" + suffix;
            String cryptoSchema = "wfproof_crypto_" + suffix;
            String runtimePassword = randomHex(24);
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            boolean created = false;
            try {
                admin.setAutoCommit(false);
                try (Statement s = admin.createStatement()) {
                    s.execute("CREATE ROLE " + owner + " NOLOGIN NOINHERIT");
                    s.execute("CREATE ROLE " + runtimeRole
                            + " LOGIN NOINHERIT PASSWORD '" + runtimePassword + "'");
                    s.execute("CREATE SCHEMA " + protectedSchema + " AUTHORIZATION " + owner);
                    s.execute("CREATE SCHEMA " + cryptoSchema);
                    s.execute("CREATE EXTENSION pgcrypto WITH SCHEMA " + cryptoSchema);
                    s.execute("REVOKE ALL ON SCHEMA " + cryptoSchema + " FROM PUBLIC");
                    s.execute("GRANT USAGE ON SCHEMA " + cryptoSchema + " TO " + owner);
                    s.execute("SET ROLE " + owner);
                    s.execute("CREATE TABLE " + protectedSchema
                            + ".proof_key (id INTEGER PRIMARY KEY, secret BYTEA NOT NULL)");
                    s.execute("CREATE TABLE " + protectedSchema
                            + ".private_data (target TEXT PRIMARY KEY, actor TEXT NOT NULL, payload TEXT NOT NULL)");
                    try (PreparedStatement insert = admin.prepareStatement(
                            "INSERT INTO " + protectedSchema + ".proof_key VALUES (1, ?)")) {
                        insert.setBytes(1, secret);
                        insert.executeUpdate();
                    }
                    try (PreparedStatement insert = admin.prepareStatement(
                            "INSERT INTO " + protectedSchema
                                    + ".private_data(target, actor, payload) VALUES (?, ?, ?)")) {
                        insert.setString(1, TARGET);
                        insert.setString(2, ACTOR);
                        insert.setString(3, VALUE);
                        insert.executeUpdate();
                    }
                    s.execute(guardedFunction(protectedSchema, cryptoSchema));
                    // Same DDL transaction: no window where PUBLIC can execute a new function.
                    s.execute("REVOKE ALL ON ALL FUNCTIONS IN SCHEMA " + protectedSchema + " FROM PUBLIC");
                    s.execute("RESET ROLE");
                    s.execute("GRANT USAGE ON SCHEMA " + protectedSchema + " TO " + runtimeRole);
                    s.execute("GRANT EXECUTE ON FUNCTION " + protectedSchema
                            + ".guarded_read(TEXT,TEXT,TEXT,TEXT,BYTEA) TO " + runtimeRole);
                }
                admin.commit();
                created = true;
                // Every security test below uses a REAL LOGIN as the restricted SQL role.
                proveNoDirectDml(url, runtimeRole, runtimePassword, protectedSchema);
                proveValidAndDeniedProofs(url, runtimeRole, runtimePassword, protectedSchema, secret);
                System.out.println("DISPOSABLE_PG17_SQL_PROOF_PASS: restricted login, no direct DML, HMAC target/action/XID");
            } catch (Exception failure) {
                if (!created) {
                    try { admin.rollback(); } catch (SQLException ignored) {}
                }
                throw failure;
            } finally {
                java.util.Arrays.fill(secret, (byte) 0);
                try {
                    admin.setAutoCommit(true);
                    try (Statement s = admin.createStatement()) {
                        s.execute("RESET ROLE");
                        s.execute("DROP SCHEMA IF EXISTS " + protectedSchema + " CASCADE");
                        if (created) s.execute("DROP EXTENSION IF EXISTS pgcrypto");
                        s.execute("DROP SCHEMA IF EXISTS " + cryptoSchema + " CASCADE");
                        s.execute("DROP ROLE IF EXISTS " + runtimeRole);
                        s.execute("DROP ROLE IF EXISTS " + owner);
                    }
                } catch (SQLException cleanupFailure) {
                    throw new IllegalStateException("Disposable test cleanup failed", cleanupFailure);
                }
            }
        }
    }

    private static void proveDisposable(Connection admin, String expectedDatabase) throws SQLException {
        try (Statement s = admin.createStatement(); ResultSet result = s.executeQuery(
                "SELECT current_database(), current_setting('server_version_num'), "
                        + "current_setting('dxos.wfproof_disposable',true), "
                        + "pg_catalog.pg_is_in_recovery(), "
                        + "current_user = session_user")) {
            if (!result.next() || !expectedDatabase.equals(result.getString(1))
                    || Integer.parseInt(result.getString(2)) / 10000 != 17
                    || !"on".equals(result.getString(3))
                    || result.getBoolean(4) || !result.getBoolean(5)) {
                throw new IllegalArgumentException("Unmarked/non-isolated/non-PG17 database rejected");
            }
        }
        // This test creates/drops roles and a pgcrypto extension only in a disposable CLUSTER.
        try (Statement s = admin.createStatement(); ResultSet result = s.executeQuery(
                "SELECT rolsuper, rolcreaterole FROM pg_catalog.pg_roles WHERE rolname = current_user")) {
            if (!result.next() || !result.getBoolean(1) || !result.getBoolean(2)) {
                throw new IllegalArgumentException("Disposable-only admin must own the test cluster");
            }
        }
        try (Statement s = admin.createStatement(); ResultSet result = s.executeQuery(
                "SELECT count(*) FROM pg_catalog.pg_extension WHERE extname='pgcrypto'")) {
            if (!result.next() || result.getInt(1) != 0) {
                throw new IllegalArgumentException("Use a fresh disposable DB without pgcrypto installed");
            }
        }
    }

    private static String guardedFunction(String schema, String crypto) {
        return """
            CREATE FUNCTION %s.guarded_read(
                p_actor TEXT, p_action TEXT, p_target TEXT, p_nonce TEXT, p_mac BYTEA)
            RETURNS TEXT
            LANGUAGE plpgsql SECURITY DEFINER
            SET search_path = pg_catalog, pg_temp
            AS $wfproof$
            DECLARE
                f TEXT;
                fields TEXT[];
                packet BYTEA := pg_catalog.int4send(7);
                protected_key BYTEA;
                expected BYTEA;
                value TEXT;
            BEGIN
                IF p_actor IS NULL OR p_action IS NULL OR p_target IS NULL
                        OR p_nonce IS NULL OR p_mac IS NULL THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                fields := ARRAY[
                    'DXOS-WF-SQL-CAP-DEMO', p_actor, p_action, p_target,
                    pg_catalog.pg_backend_pid()::TEXT,
                    pg_catalog.pg_current_xact_id()::TEXT, p_nonce
                ];
                FOREACH f IN ARRAY fields LOOP
                    IF pg_catalog.octet_length(pg_catalog.convert_to(f,'UTF8')) > 4096 THEN
                        RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                    END IF;
                    packet := packet
                        || pg_catalog.int4send(pg_catalog.octet_length(pg_catalog.convert_to(f,'UTF8')))
                        || pg_catalog.convert_to(f,'UTF8');
                END LOOP;
                SELECT secret INTO protected_key FROM %s.proof_key WHERE id = 1;
                expected := %s.hmac(packet, protected_key, 'sha256');
                -- TEST PROTOTYPE ONLY: BYTEA '=' is not proven constant-time.
                IF expected IS DISTINCT FROM p_mac THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                IF p_action <> 'READ' THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                SELECT payload INTO value FROM %s.private_data
                    WHERE target = p_target AND actor = p_actor;
                IF value IS NULL THEN
                    RAISE EXCEPTION 'denied' USING ERRCODE='42501';
                END IF;
                RETURN value;
            END
            $wfproof$
            """.formatted(schema, schema, crypto, schema);
        // PostgreSQL TEXT itself rejects NUL; the Java canonical codec also rejects it.
    }

    private static void proveNoDirectDml(String url, String user, String password, String schema)
            throws SQLException {
        assertDenied(url, user, password, "SELECT * FROM " + schema + ".private_data");
        assertDenied(url, user, password, "SELECT * FROM " + schema + ".proof_key");
        assertDenied(url, user, password,
                "UPDATE " + schema + ".private_data SET payload='tampered' WHERE target='workflow:A'");
        assertDenied(url, user, password, "DELETE FROM " + schema + ".private_data");
        assertDenied(url, user, password, "CREATE TABLE " + schema + ".bypass(id INTEGER)");
    }

    private static void proveValidAndDeniedProofs(String url, String user, String password,
                                                  String schema, byte[] secret) throws SQLException {
        byte[] signed;
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            String[] binding = transactionBinding(c);
            signed = WorkflowSqlProofPrototype.hmacSha256(secret, WorkflowSqlProofPrototype.canonical(
                    ACTOR, ACTION, TARGET, binding[0], binding[1], "fresh-nonce"));
            String actual = call(c, schema, ACTOR, ACTION, TARGET, "fresh-nonce", signed);
            if (!VALUE.equals(actual)) throw new AssertionError("Signed permitted read mismatch");
            c.commit();
            // Attempt to reuse the exact signed proof in a subsequent transaction.
            expectDenied(c, schema, ACTOR, ACTION, TARGET, "fresh-nonce", signed);
        }
        // Each altered parameter is tried with a fresh transaction and a valid signature for A.
        tamperedCall(url, user, password, schema, secret, "oidc:forged", ACTION, TARGET);
        tamperedCall(url, user, password, schema, secret, ACTOR, ACTION, "workflow:B");
        tamperedCall(url, user, password, schema, secret, ACTOR, "CREATE", TARGET);
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            transactionBinding(c);
            expectDenied(c, schema, ACTOR, ACTION, TARGET, "no-proof", new byte[32]);
        }
    }

    private static void tamperedCall(String url, String user, String password, String schema,
                                     byte[] secret, String actualActor, String actualAction,
                                     String actualTarget) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            String[] binding = transactionBinding(c);
            byte[] mac = WorkflowSqlProofPrototype.hmacSha256(secret,
                    WorkflowSqlProofPrototype.canonical(
                            ACTOR, ACTION, TARGET, binding[0], binding[1], "fresh-nonce"));
            expectDenied(c, schema, actualActor, actualAction, actualTarget, "fresh-nonce", mac);
        }
    }

    private static String[] transactionBinding(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet result = s.executeQuery(
                "SELECT pg_catalog.pg_backend_pid()::text, pg_catalog.pg_current_xact_id()::text")) {
            if (!result.next()) throw new SQLException("Missing transaction binding");
            return new String[]{result.getString(1), result.getString(2)};
        }
    }

    private static String call(Connection c, String schema, String actor, String action,
                               String target, String nonce, byte[] mac) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT " + schema + ".guarded_read(?, ?, ?, ?, ?)")) {
            s.setString(1, actor);
            s.setString(2, action);
            s.setString(3, target);
            s.setString(4, nonce);
            s.setBytes(5, mac);
            try (ResultSet rows = s.executeQuery()) {
                if (!rows.next()) throw new AssertionError("No guarded result");
                return rows.getString(1);
            }
        }
    }

    private static void expectDenied(Connection c, String schema, String actor, String action,
                                     String target, String nonce, byte[] mac) throws SQLException {
        try {
            call(c, schema, actor, action, target, nonce, mac);
            throw new AssertionError("SQL proof unexpectedly granted permission");
        } catch (SQLException denied) {
            if (!"42501".equals(denied.getSQLState())) {
                throw new AssertionError("Unexpected SQLSTATE for proof rejection", denied);
            }
            c.rollback();
        }
    }

    private static void assertDenied(String url, String user, String password, String sql)
            throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             Statement s = c.createStatement()) {
            try {
                s.execute(sql);
                throw new AssertionError("Restricted SQL role unexpectedly succeeded");
            } catch (SQLException denied) {
                if (!"42501".equals(denied.getSQLState())) {
                    throw new AssertionError("Unexpected SQLSTATE for privilege denial", denied);
                }
            }
        }
    }

    private static String required(String name) {
        String result = System.getenv(name);
        if (result == null || result.isBlank()) throw new IllegalArgumentException(name + " required");
        return result;
    }

    private static String randomHex(int bytes) {
        byte[] raw = new byte[bytes];
        new SecureRandom().nextBytes(raw);
        return HexFormat.of().formatHex(raw).toLowerCase(Locale.ROOT);
    }
}
