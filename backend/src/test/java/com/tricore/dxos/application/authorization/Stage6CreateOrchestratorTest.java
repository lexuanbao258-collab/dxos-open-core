package com.tricore.dxos.application.authorization;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Application orchestration only; does not claim a guarded JDBC/PG17 result. */
class Stage6CreateOrchestratorTest {
    private static final String ACTOR = "oidc:verified-issuer.verified-subject";
    private static final String TENANT = "demo-tenant";
    private static final ResourceReference RESOURCE = new ResourceReference("item", "item-1");
    private static final WorkflowDefinition DEFINITION =
            new WorkflowDefinition("publication", 7, "draft", Set.of("draft"), Set.of());
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);

    private final CurrentPrincipal identity = mock(CurrentPrincipal.class);
    private final Stage6CreateGate.Source source = mock(Stage6CreateGate.Source.class);
    private final WorkflowPersistencePort port = mock(WorkflowPersistencePort.class);
    private final Stage6CreateOrchestrator.ScopedPersistenceFactory factory =
            mock(Stage6CreateOrchestrator.ScopedPersistenceFactory.class);
    private final Stage6CreateOrchestrator orchestrator =
            new Stage6CreateOrchestrator(new Stage6CreateGate(identity, source), factory, CLOCK);

    @BeforeEach
    void setup() {
        verified("workflow:create");
        when(source.classify(RESOURCE, ACTOR)).thenReturn(Optional.of(new Stage6CreateGate.Facts(
                RESOURCE, ACTOR, TENANT, true, true, false, Set.of(WorkflowOperation.CREATE))));
        when(source.hasExactActiveBinding(RESOURCE, TENANT, "publication", 7)).thenReturn(true);
        when(factory.forCreate(any(), anyString(), eq(RESOURCE), eq("publication"), eq(7L)))
                .thenReturn(port);
        when(port.loadDefinition("publication", 7)).thenReturn(DEFINITION);
        when(port.createInstance(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createsServerOwnedOpaqueIdWithoutLegacyDuplicateProbe() {
        WorkflowInstance created = create();

        assertThat(created.instanceId()).matches("wf_[A-Za-z0-9_-]{32}");
        assertThat(created.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(created.updatedAt()).isEqualTo(CLOCK.instant());
        assertThat(created.runtimeVersion()).isZero();
        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        InOrder sequence = inOrder(identity, source, factory, port);
        sequence.verify(identity).requireVerified();
        sequence.verify(source).classify(RESOURCE, ACTOR);
        sequence.verify(source).hasExactActiveBinding(RESOURCE, TENANT, "publication", 7);
        sequence.verify(factory).forCreate(any(), id.capture(), eq(RESOURCE), eq("publication"), eq(7L));
        sequence.verify(port).loadDefinition("publication", 7);
        sequence.verify(port).createInstance(any(WorkflowInstance.class));
        sequence.verifyNoMoreInteractions();
        assertThat(id.getValue()).isEqualTo(created.instanceId());
        verify(port, never()).loadInstance(anyString());
    }

    @Test
    void createsDifferentRandomIdsForIndependentRequests() {
        String first = create().instanceId();
        String second = create().instanceId();
        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("wf_[A-Za-z0-9_-]{32}");
        assertThat(second).matches("wf_[A-Za-z0-9_-]{32}");
    }

    @Test
    void hiddenResourceCannotOpenScopedPortOrReadDefinition() {
        when(source.classify(RESOURCE, ACTOR)).thenReturn(Optional.empty());

        assertThatThrownBy(this::create).isInstanceOf(WorkflowAccessException.class)
                .hasMessage(WorkflowAccessDecision.HIDDEN.name());
        verifyNoInteractions(factory, port);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void missingExactAuthorityCannotMaterializeDefinitionOrOpenPort() {
        verified("workflow:read");

        assertThatThrownBy(this::create).isInstanceOf(WorkflowAccessException.class)
                .hasMessage(WorkflowAccessDecision.FORBIDDEN.name());
        verifyNoInteractions(factory, port);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void mismatchedDefinitionNeverWrites() {
        when(port.loadDefinition("publication", 7)).thenReturn(
                new WorkflowDefinition("foreign", 7, "draft", Set.of("draft"), Set.of()));

        assertThatThrownBy(this::create).isInstanceOf(WorkflowAccessException.class)
                .hasMessage(WorkflowAccessDecision.UNAVAILABLE.name());
        verify(port, never()).createInstance(any());
    }

    @Test
    void uncertainCommitPropagatesWithoutAutomaticRetry() {
        WorkflowException unknown = new WorkflowException(WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN);
        when(port.createInstance(any())).thenThrow(unknown);

        assertThatThrownBy(this::create).isSameAs(unknown);
        verify(port, times(1)).createInstance(any());
        verify(port, never()).loadInstance(anyString());
    }

    private void verified(String authority) {
        when(identity.requireVerified()).thenReturn(new AuthenticatedPrincipal(
                ACTOR, null, Set.of(new Authority(authority)), true));
    }

    private WorkflowInstance create() {
        return orchestrator.create(RESOURCE, "publication", 7);
    }
}
