package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Explicit, isolated PostgreSQL 17-only opt-in. No shared DB connection is attempted
 * during normal Gradle builds: the test is SKIPPED absent the exact disposable marker.
 * See docs/verification/issue-23-disposable-pg17-hmac-privilege-probe.md.
 */
@EnabledIfEnvironmentVariable(named = "WORKFLOW_SQL_PROOF_DISPOSABLE_ACK", matches = "ONLY_DISPOSABLE_PG17")
class WorkflowSqlPrivilegeDisposableIntegrationTest {
    @Test
    void restrictedRuntimeCredentialCannotBypassHmacGuardedRead() throws Exception {
        WorkflowSqlPrivilegeDisposableProbe.runFromEnvironment();
    }
}
