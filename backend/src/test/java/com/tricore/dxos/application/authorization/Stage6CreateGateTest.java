package com.tricore.dxos.application.authorization;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import com.tricore.dxos.core.workflow.ResourceReference;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Stage 6 precheck evidence only: not a live SQL/Identity/HTTP integration test. */
class Stage6CreateGateTest {
    private static final String ACTOR = "oidc:verified-issuer.verified-subject";
    private static final String TENANT = "synthetic-tenant-a";
    private static final ResourceReference RESOURCE = new ResourceReference("document", "resource-1");

    private final CurrentPrincipal principal = mock(CurrentPrincipal.class);
    private final Stage6CreateGate.Source source = mock(Stage6CreateGate.Source.class);
    private final Stage6CreateGate gate = new Stage6CreateGate(principal, source);

    @Test
    void identityFailureIsNotReplacedAndDoesNotReadAnyAcl() {
        IllegalStateException failure = new IllegalStateException("No verified OIDC identity");
        when(principal.requireVerified()).thenThrow(failure);

        assertThatThrownBy(this::check).isSameAs(failure);
        verifyNoInteractions(source);
    }

    @Test
    void missingResourceIsHiddenWithoutReadingBinding() {
        identity("workflow:create");
        when(source.classify(RESOURCE, ACTOR)).thenReturn(Optional.empty());

        denied(WorkflowAccessDecision.HIDDEN);
        verify(source).classify(RESOURCE, ACTOR);
        verifyNoMoreInteractions(source);
    }

    @Test
    void inactiveMembershipIsHiddenEvenWithAuthorityAndCreateGrant() {
        identity("workflow:create");
        facts(true, false, false, Set.of(WorkflowOperation.CREATE));
        denied(WorkflowAccessDecision.HIDDEN);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void tombstonedResourceIsHiddenBeforeAuthorityDenial() {
        identity();
        facts(false, true, true, Set.of(WorkflowOperation.CREATE));
        denied(WorkflowAccessDecision.HIDDEN);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void noVisibilityIsHiddenEvenWhenAuthorityIsMissing() {
        identity();
        facts(true, true, false, Set.of());
        denied(WorkflowAccessDecision.HIDDEN);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void visibleOwnerWithoutCreateGrantIsForbidden() {
        identity("workflow:create");
        facts(true, true, true, Set.of());
        denied(WorkflowAccessDecision.FORBIDDEN);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void otherOperationGrantMakesResourceVisibleButNotCreatable() {
        identity("workflow:create");
        facts(true, true, false, Set.of(WorkflowOperation.READ));
        denied(WorkflowAccessDecision.FORBIDDEN);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void wrongJwtAuthorityIsForbiddenBeforeBinding() {
        identity("workflow:read");
        facts(true, true, false, Set.of(WorkflowOperation.CREATE));
        denied(WorkflowAccessDecision.FORBIDDEN);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void absentExactBindingIsHiddenOnlyAfterPositiveAclAndAuthority() {
        identity("workflow:create");
        facts(true, true, false, Set.of(WorkflowOperation.CREATE));
        when(source.hasExactActiveBinding(RESOURCE, TENANT, "publication", 7)).thenReturn(false);

        denied(WorkflowAccessDecision.HIDDEN);
        InOrder sequence = inOrder(source);
        sequence.verify(source).classify(RESOURCE, ACTOR);
        sequence.verify(source).hasExactActiveBinding(RESOURCE, TENANT, "publication", 7);
        sequence.verifyNoMoreInteractions();
    }

    @Test
    void validAclAuthorityAndExactBindingMayPassOnlyThePrecheck() {
        identity("workflow:create");
        facts(true, true, false, Set.of(WorkflowOperation.CREATE));
        when(source.hasExactActiveBinding(RESOURCE, TENANT, "publication", 7)).thenReturn(true);

        assertThatCode(this::check).doesNotThrowAnyException();
        InOrder sequence = inOrder(principal, source);
        sequence.verify(principal).requireVerified();
        sequence.verify(source).classify(RESOURCE, ACTOR);
        sequence.verify(source).hasExactActiveBinding(RESOURCE, TENANT, "publication", 7);
        sequence.verifyNoMoreInteractions();
    }

    @Test
    void mismatchedAclIdentityIsUnavailableBeforeBinding() {
        identity("workflow:create");
        when(source.classify(RESOURCE, ACTOR)).thenReturn(Optional.of(
                new Stage6CreateGate.Facts(RESOURCE, "different-actor", TENANT,
                        true, true, true, Set.of(WorkflowOperation.CREATE))));
        denied(WorkflowAccessDecision.UNAVAILABLE);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void failureInSourceIsUnavailableNotHiddenOrAllowed() {
        identity("workflow:create");
        when(source.classify(RESOURCE, ACTOR)).thenThrow(new IllegalStateException("private SQL failure"));
        denied(WorkflowAccessDecision.UNAVAILABLE);
        verify(source, never()).hasExactActiveBinding(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void bindingSourceFailureIsUnavailable() {
        identity("workflow:create");
        facts(true, true, false, Set.of(WorkflowOperation.CREATE));
        when(source.hasExactActiveBinding(RESOURCE, TENANT, "publication", 7))
                .thenThrow(new IllegalStateException("private SQL failure"));
        denied(WorkflowAccessDecision.UNAVAILABLE);
    }

    private void check() {
        gate.check(RESOURCE, "publication", 7);
    }

    private void identity(String... authorities) {
        Set<Authority> scopes = java.util.Arrays.stream(authorities)
                .map(Authority::new).collect(java.util.stream.Collectors.toUnmodifiableSet());
        when(principal.requireVerified()).thenReturn(new AuthenticatedPrincipal(ACTOR, null, scopes, true));
    }

    private void facts(boolean resourceActive, boolean memberActive, boolean owner,
                       Set<WorkflowOperation> grants) {
        when(source.classify(RESOURCE, ACTOR)).thenReturn(Optional.of(
                new Stage6CreateGate.Facts(RESOURCE, ACTOR, TENANT,
                        resourceActive, memberActive, owner, grants)));
    }

    private void denied(WorkflowAccessDecision expected) {
        assertThatThrownBy(this::check).isInstanceOf(WorkflowAccessException.class)
                .hasMessage(expected.name()).hasNoCause();
    }
}
