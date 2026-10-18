package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowSqlProofPrototypeTest {
    @Test
    void jdk21Rfc4231Utf8TargetActionAndTransactionBinding() {
        WorkflowSqlProofPrototype.main(new String[0]);
        byte[] packet = WorkflowSqlProofPrototype.canonical(
                "oidc:é-🚀", "READ", "workflow:A", "4321", "988", "nonce-1");
        assertThat(packet).isNotEmpty();
        assertThat(packet[0]).isZero();
        assertThat(packet[3]).isEqualTo((byte) 7);
    }
}
