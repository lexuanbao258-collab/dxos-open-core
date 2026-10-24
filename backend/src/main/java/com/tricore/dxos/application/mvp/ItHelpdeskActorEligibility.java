package com.tricore.dxos.application.mvp;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/**
 * Narrow business-actor predicates for IT Helpdesk.
 *
 * This is NOT authentication, JWT authority validation, a coordinator ACL,
 * a resource visibility policy, or a final transactional ACL guard.
 * The caller MUST be supplied by CurrentPrincipal.requireVerified() and both persisted IDs
 * MUST originate from authoritative Application records, never a request body/header.
 * Resource/operation policy checks are separately mandatory for every LIVE operation.
 */
public final class ItHelpdeskActorEligibility {
    private ItHelpdeskActorEligibility() {}

    /** Only the original requester can confirm a resolved IT request. */
    public static void requireOriginalRequester(AuthenticatedPrincipal verifiedCaller,
                                                String persistedRequesterActorId) {
        requireActor(verifiedCaller, persistedRequesterActorId);
    }

    /** Only the current assigned IT worker can start or resolve their request. */
    public static void requireCurrentAssignee(AuthenticatedPrincipal verifiedCaller,
                                               String persistedCurrentAssigneeActorId) {
        requireActor(verifiedCaller, persistedCurrentAssigneeActorId);
    }

    private static void requireActor(AuthenticatedPrincipal caller, String persistedActorId) {
        Objects.requireNonNull(caller, "verified caller");
        if (persistedActorId == null || persistedActorId.isBlank()
                || !caller.subjectId().equals(persistedActorId)) {
            throw new AccessDeniedException("Business actor is not eligible");
        }
    }
}
