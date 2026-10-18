package com.tricore.dxos.application.workflow;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class WorkflowInstanceIdGeneratorTest {
    @Test
    void generatesOpaqueIdentifiersCompatibleWithExistingHttpPath() {
        var generator = new WorkflowInstanceIdGenerator();
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.nextId()).matches("wf_[A-Za-z0-9_-]{32}");
        }
    }

    @Test
    void producesDistinctIdentifiersAcrossGenerationsAndInstances() {
        var first = new WorkflowInstanceIdGenerator();
        var second = new WorkflowInstanceIdGenerator();
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            assertThat(values.add(first.nextId())).isTrue();
            assertThat(values.add(second.nextId())).isTrue();
        }
        assertThat(values).hasSize(10_000);
    }

    @Test
    void rejectsNullRandomSource() {
        assertThatThrownBy(() -> new WorkflowInstanceIdGenerator(null))
                .isInstanceOf(NullPointerException.class);
    }
}
