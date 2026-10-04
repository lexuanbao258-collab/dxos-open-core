package com.tricore.dxos.request.audit;

import com.tricore.dxos.request.audit.domain.InvalidAuditActorException;
import com.tricore.dxos.request.audit.service.AuditActorRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class AuditActorRefTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void missingActorUsesAnonymous(String actor) {
        assertThat(AuditActorRef.resolve(actor)).isEqualTo("anonymous");
    }

    @Test
    void normalizesAttributionAndRejectsUnboundedOrControlData() {
        assertThat(AuditActorRef.resolve(" user-001 ")).isEqualTo("user-001");
        assertThatThrownBy(() -> AuditActorRef.resolve("a".repeat(101))).isInstanceOf(InvalidAuditActorException.class);
        assertThatThrownBy(() -> AuditActorRef.resolve("user\n001")).isInstanceOf(InvalidAuditActorException.class);
    }
}
