package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in disposable PG17 ONLY: six reduced SQL port APIs, key rotation, and actual
 * two-physical-connection tenant guard / ACL revocation lock ordering.
 *
 * Never uses the staging or Supabase PostgreSQL database and never runs by default.
 */
@EnabledIfEnvironmentVariable(named = "WORKFLOW_SQL_PROOF_DISPOSABLE_ACK", matches = "ONLY_DISPOSABLE_PG17")
class WorkflowSixPortDisposableContractIntegrationTest {
    @Test
    void sixSqlPortMethodsHmacKeyRotationAndAclRaceContract() throws Exception {
        WorkflowSixPortDisposableContractProbe.runFromEnvironment();
    }
}
