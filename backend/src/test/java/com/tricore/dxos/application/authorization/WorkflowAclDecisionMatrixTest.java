package com.tricore.dxos.application.authorization;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exhaustive decision/authority matrix for the existing pure WorkflowAuthorization collaborator.
 *
 * Policy responses and callers here are SYNTHETIC TEST FIXTURES. These tests DO NOT implement or
 * verify real tenant membership, persistent resource ACL, JDBC guard locks, OIDC, HTTP, or revocation.
 * See docs/contracts/issue-23-acl-test-plan.md before declaring Issue #23 complete.
 */
class WorkflowAclDecisionMatrixTest {
    private static final WorkflowDefinition DEFINITION =
            new WorkflowDefinition("publication", 7, "draft", Set.of("draft"), Set.of());
    private static final ResourceReference RESOURCE =
            new ResourceReference("document", "document-1");
    private static final WorkflowInstance INSTANCE = WorkflowInstance.notStarted(
            "wf_test-1", DEFINITION, RESOURCE, Instant.parse("2026-10-17T00:00:00Z"));

    enum AuthorityFixture {
        EXACT, NONE, OTHER_OPERATION, WILDCARD
    }

    @ParameterizedTest(name = "{index}: operation={0}, decision={1}, authority={2}")
    @MethodSource("decisionMatrix")
    void honorsBothExactOperationAuthorityAndPolicyDecision(
            WorkflowOperation operation, WorkflowAccessDecision decision, AuthorityFixture authorityFixture) {
        RecordingPolicy policy = new RecordingPolicy(decision, false);
        WorkflowAuthorization authorization = new WorkflowAuthorization(policy);
        AuthenticatedPrincipal caller = caller(operation, authorityFixture);

        WorkflowAccessDecision expected = decision != WorkflowAccessDecision.ALLOWED
                ? decision
                : authorityFixture == AuthorityFixture.EXACT ? null : WorkflowAccessDecision.FORBIDDEN;

        if (expected == null) {
            assertThatCode(() -> authorize(authorization, operation, caller)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> authorize(authorization, operation, caller))
                    .isInstanceOf(WorkflowAccessException.class)
                    .hasMessage(expected.name())
                    .hasNoCause()
                    .satisfies(error -> assertThat(((WorkflowAccessException) error).decision())
                            .isEqualTo(expected));
        }

        // Do not short-circuit visibility/resource policy because of absent token authority.
        assertThat(policy.calls).isEqualTo(1);
        assertThat(policy.observedOperation).isEqualTo(operation);
    }

    private static Stream<Arguments> decisionMatrix() {
        return Stream.of(WorkflowOperation.values()).flatMap(operation ->
                Stream.of(WorkflowAccessDecision.values()).flatMap(decision ->
                        Stream.of(AuthorityFixture.values()).map(authority ->
                                Arguments.of(operation, decision, authority))));
    }

    @ParameterizedTest
    @EnumSource(WorkflowOperation.class)
    void nullResourcePolicyDecisionAlwaysFailsClosed(WorkflowOperation operation) {
        RecordingPolicy policy = new RecordingPolicy(null, false);
        WorkflowAuthorization authorization = new WorkflowAuthorization(policy);

        assertThatThrownBy(() -> authorize(authorization, operation, caller(operation, AuthorityFixture.EXACT)))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("UNAVAILABLE")
                .hasNoCause();
        assertThat(policy.calls).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(WorkflowOperation.class)
    void policyProviderExceptionIsSanitizedAndNeverGrantsAccess(WorkflowOperation operation) {
        RecordingPolicy policy = new RecordingPolicy(null, true);
        WorkflowAuthorization authorization = new WorkflowAuthorization(policy);

        assertThatThrownBy(() -> authorize(authorization, operation, caller(operation, AuthorityFixture.EXACT)))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("UNAVAILABLE")
                .hasNoCause()
                .hasNoSuppressedExceptions();
        assertThat(policy.calls).isEqualTo(1);
    }

    @Test
    void hiddenAndAbsentCreationTargetsHaveIdenticalPolicyLevelDenials() {
        WorkflowAccessPolicy policy = new WorkflowAccessPolicy() {
            @Override
            public WorkflowAccessDecision forCreation(AuthenticatedPrincipal caller, WorkflowDefinition definition,
                                                      ResourceReference resource) {
                return WorkflowAccessDecision.HIDDEN; // synthetic hidden and nonexistent cases
            }

            @Override
            public WorkflowAccessDecision forInstance(AuthenticatedPrincipal caller, WorkflowInstance instance,
                                                      WorkflowOperation operation) {
                return WorkflowAccessDecision.HIDDEN;
            }
        };
        WorkflowAuthorization authorization = new WorkflowAuthorization(policy);
        AuthenticatedPrincipal caller = caller(WorkflowOperation.CREATE, AuthorityFixture.EXACT);

        for (String resourceId : new String[]{"hidden-document", "missing-document"}) {
            assertThatThrownBy(() -> authorization.authorizeCreation(caller, DEFINITION,
                    new ResourceReference("document", resourceId)))
                    .isInstanceOf(WorkflowAccessException.class)
                    .hasMessage("HIDDEN")
                    .hasNoCause();
        }
    }

    private static void authorize(WorkflowAuthorization authorization, WorkflowOperation operation,
                                  AuthenticatedPrincipal caller) {
        if (operation == WorkflowOperation.CREATE) {
            authorization.authorizeCreation(caller, DEFINITION, RESOURCE);
        } else {
            authorization.authorizeInstance(caller, INSTANCE, operation);
        }
    }

    private static AuthenticatedPrincipal caller(WorkflowOperation operation, AuthorityFixture fixture) {
        String exact = operation.requiredAuthority().value();
        String unrelated = operation == WorkflowOperation.CREATE ? "workflow:read" : "workflow:create";
        Set<Authority> authorities = switch (fixture) {
            case EXACT -> Set.of(new Authority(exact));
            case NONE -> Set.of();
            case OTHER_OPERATION -> Set.of(new Authority(unrelated));
            case WILDCARD -> Set.of(new Authority("workflow:*"));
        };
        return new AuthenticatedPrincipal("synthetic-test-caller", null, authorities, true);
    }

    private static final class RecordingPolicy implements WorkflowAccessPolicy {
        private final WorkflowAccessDecision decision;
        private final boolean throwsProviderError;
        private int calls;
        private WorkflowOperation observedOperation;

        private RecordingPolicy(WorkflowAccessDecision decision, boolean throwsProviderError) {
            this.decision = decision;
            this.throwsProviderError = throwsProviderError;
        }

        @Override
        public WorkflowAccessDecision forCreation(AuthenticatedPrincipal caller, WorkflowDefinition definition,
                                                  ResourceReference resource) {
            assertThat(definition).isEqualTo(DEFINITION);
            assertThat(resource).isEqualTo(RESOURCE);
            return decide(WorkflowOperation.CREATE);
        }

        @Override
        public WorkflowAccessDecision forInstance(AuthenticatedPrincipal caller, WorkflowInstance instance,
                                                  WorkflowOperation operation) {
            assertThat(instance).isSameAs(INSTANCE);
            return decide(operation);
        }

        private WorkflowAccessDecision decide(WorkflowOperation operation) {
            calls++;
            observedOperation = operation;
            if (throwsProviderError) {
                throw new IllegalStateException("private ACL provider detail MUST NOT LEAK");
            }
            return decision;
        }
    }
}
