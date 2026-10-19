package com.tricore.dxos.gateway.workflow.security;

import com.tricore.dxos.application.authorization.WorkflowOperation;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Minimal Identity -> Gateway handoff. Uses the trusted Identity boundary exactly once;
 * it does NOT decide tenant membership, resource ACL, or permission to read protected data.
 *
 * Do not connect this class directly to the unregistered WorkflowController: production
 * Workflow HTTP still requires AuthorizedWorkflowService and guarded PostgreSQL operations.
 */
@Component
public final class WorkflowGatewayPrincipalGate {
    private final CurrentPrincipal currentPrincipal;

    public WorkflowGatewayPrincipalGate(CurrentPrincipal currentPrincipal) {
        this.currentPrincipal = Objects.requireNonNull(currentPrincipal, "currentPrincipal");
    }

    public AuthenticatedPrincipal requireOperation(WorkflowOperation operation) {
        Objects.requireNonNull(operation, "operation");
        AuthenticatedPrincipal principal = currentPrincipal.requireVerified();
        if (principal.authorities().stream()
                .noneMatch(authority -> authority.satisfies(operation.requiredAuthority()))) {
            throw new AccessDeniedException("Workflow operation is not permitted");
        }
        return principal;
    }
}
