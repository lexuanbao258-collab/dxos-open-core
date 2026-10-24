package com.tricore.dxos.application.mvp;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItHelpdeskActorEligibilityTest {
    private final AuthenticatedPrincipal verified =
            new AuthenticatedPrincipal("oidc:issuer-subject-a", "Worker A", Set.of(), true);

    @Test
    void requesterCanConfirmOnlyOwnPersistedRequest() {
        assertThatCode(() -> ItHelpdeskActorEligibility.requireOriginalRequester(
                verified, "oidc:issuer-subject-a")).doesNotThrowAnyException();

        assertThatThrownBy(() -> ItHelpdeskActorEligibility.requireOriginalRequester(
                verified, "oidc:issuer-subject-b")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void formerAssigneeCannotResolveForDifferentCurrentAssignee() {
        assertThatThrownBy(() -> ItHelpdeskActorEligibility.requireCurrentAssignee(
                verified, "oidc:issuer-subject-b")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingPersistedBusinessActorFailsClosed() {
        assertThatThrownBy(() -> ItHelpdeskActorEligibility.requireCurrentAssignee(
                verified, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ItHelpdeskActorEligibility.requireOriginalRequester(
                verified, " ")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nullVerifiedCallerIsNotAccepted() {
        assertThatThrownBy(() -> ItHelpdeskActorEligibility.requireOriginalRequester(
                null, "oidc:issuer-subject-a")).isInstanceOf(NullPointerException.class);
    }
}
