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
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic caller/policy fixtures test authorization only, never real authentication or ACLs. */
class WorkflowAuthorizationTest {
    private static final WorkflowDefinition DEFINITION = new WorkflowDefinition(
            "publication", 7, "draft", Set.of("draft"), Set.of());
    private static final ResourceReference RESOURCE = new ResourceReference("document", "document-1");
    private static final WorkflowInstance INSTANCE = WorkflowInstance.notStarted(
            "instance-1", DEFINITION, RESOURCE, Instant.parse("2026-10-11T00:00:00Z"));
    private final TestPolicy policy = new TestPolicy();
    private final WorkflowAuthorization authorization = new WorkflowAuthorization(policy);

    @Test
    void rejectsCreationWithoutRequiredAuthority() {
        AuthenticatedPrincipal caller = new AuthenticatedPrincipal("synthetic-caller", null, Set.of(), true);

        assertThatThrownBy(() -> authorization.authorizeCreation(caller, DEFINITION, RESOURCE))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("FORBIDDEN");
    }

    @Test
    void permitsCreationOnlyWithOperationAuthorityAndResourcePermission() {
        assertThatCode(() -> authorization.authorizeCreation(caller("workflow:create"), DEFINITION, RESOURCE))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowOperation.class, names = {"ACTIVATE", "TRANSITION", "READ"})
    void rejectsInstanceAccessWithoutRequiredAuthority(WorkflowOperation operation) {
        assertThatThrownBy(() -> authorization.authorizeInstance(caller("workflow:create"), INSTANCE, operation))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("FORBIDDEN");
    }

    @ParameterizedTest
    @EnumSource(WorkflowOperation.class)
    void treatsAbsentPolicyDecisionAsUnavailable(WorkflowOperation operation) {
        policy.decision = null;

        assertThatThrownBy(() -> authorize(operation, caller("workflow:create")))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("UNAVAILABLE");
    }

    @ParameterizedTest
    @EnumSource(WorkflowOperation.class)
    void sanitizesPolicyFailureAndNeverGrantsAccess(WorkflowOperation operation) {
        policy.failure = new IllegalStateException("provider failure detail");

        assertThatThrownBy(() -> authorize(operation, caller("workflow:create")))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("UNAVAILABLE")
                .hasNoCause();
    }

    @ParameterizedTest
    @MethodSource("instanceAuthorities")
    void permitsInstanceOperationWithItsExactAuthorityAndResourcePermission(
            WorkflowOperation operation, String authority) {
        policy.expectedOperation = operation;

        assertThatCode(() -> authorization.authorizeInstance(caller(authority), INSTANCE, operation))
                .doesNotThrowAnyException();
    }

    private static Stream<Arguments> instanceAuthorities() {
        return Stream.of(
                Arguments.of(WorkflowOperation.ACTIVATE, "workflow:activate"),
                Arguments.of(WorkflowOperation.TRANSITION, "workflow:transition"),
                Arguments.of(WorkflowOperation.READ, "workflow:read"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"workflow:*", "WORKFLOW:CREATE", " workflow:create", "workflow:create "})
    void rejectsWildcardCaseChangesAndWhitespaceInsteadOfCanonicalAuthority(String authority) {
        assertThatThrownBy(() -> authorization.authorizeCreation(caller(authority), DEFINITION, RESOURCE))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("FORBIDDEN");
    }

    @ParameterizedTest
    @MethodSource("resourceDenials")
    void operationAuthorityNeverOverridesResourceDenial(
            WorkflowOperation operation, WorkflowAccessDecision decision, String expectedMessage) {
        policy.decision = decision;

        assertThatThrownBy(() -> authorize(operation, callerWithAllAuthorities()))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage(expectedMessage)
                .hasNoCause()
                .satisfies(failure -> assertThat(((WorkflowAccessException) failure).decision()).isEqualTo(decision));
    }

    private static Stream<Arguments> resourceDenials() {
        return Stream.of(WorkflowOperation.values()).flatMap(operation -> Stream.of(
                Arguments.of(operation, WorkflowAccessDecision.FORBIDDEN, "FORBIDDEN"),
                Arguments.of(operation, WorkflowAccessDecision.HIDDEN, "HIDDEN"),
                Arguments.of(operation, WorkflowAccessDecision.UNAVAILABLE, "UNAVAILABLE")));
    }

    @ParameterizedTest
    @EnumSource(WorkflowOperation.class)
    void hiddenResourceRemainsHiddenEvenWithoutOperationAuthority(WorkflowOperation operation) {
        policy.decision = WorkflowAccessDecision.HIDDEN;
        AuthenticatedPrincipal caller = new AuthenticatedPrincipal("synthetic-caller", null, Set.of(), true);

        assertThatThrownBy(() -> authorize(operation, caller))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("HIDDEN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"hidden-document", "missing-document"})
    void hiddenAndMissingResourceFixturesHaveTheSameSanitizedDenial(String resourceId) {
        ResourceReference resource = new ResourceReference("document", resourceId);

        assertThatThrownBy(() -> authorization.authorizeCreation(caller("workflow:create"), DEFINITION, resource))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage("HIDDEN")
                .hasNoCause();
    }

    @Test
    void rejectsCreateAsAnInstanceOperation() {
        assertThatThrownBy(() -> authorization.authorizeInstance(caller("workflow:create"), INSTANCE,
                WorkflowOperation.CREATE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAPolicyInsteadOfUsingAnAllowAllFallback() {
        assertThatThrownBy(() -> new WorkflowAuthorization(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"creation-caller", "definition", "resource", "instance-caller", "instance", "operation"})
    void rejectsIncompleteCallerOrAuthoritativeInputs(String missing) {
        AuthenticatedPrincipal caller = callerWithAllAuthorities();

        assertThatThrownBy(() -> {
            switch (missing) {
                case "creation-caller" -> authorization.authorizeCreation(null, DEFINITION, RESOURCE);
                case "definition" -> authorization.authorizeCreation(caller, null, RESOURCE);
                case "resource" -> authorization.authorizeCreation(caller, DEFINITION, null);
                case "instance-caller" -> authorization.authorizeInstance(null, INSTANCE, WorkflowOperation.READ);
                case "instance" -> authorization.authorizeInstance(caller, null, WorkflowOperation.READ);
                case "operation" -> authorization.authorizeInstance(caller, INSTANCE, null);
                default -> throw new AssertionError("Unknown test case");
            }
        }).isInstanceOf(NullPointerException.class);
    }

    private void authorize(WorkflowOperation operation, AuthenticatedPrincipal caller) {
        if (operation == WorkflowOperation.CREATE) {
            authorization.authorizeCreation(caller, DEFINITION, RESOURCE);
        } else {
            authorization.authorizeInstance(caller, INSTANCE, operation);
        }
    }

    private static AuthenticatedPrincipal caller(String authority) {
        return new AuthenticatedPrincipal("synthetic-caller", null, Set.of(new Authority(authority)), true);
    }

    private static AuthenticatedPrincipal callerWithAllAuthorities() {
        return new AuthenticatedPrincipal("synthetic-caller", null, Set.of(
                new Authority("workflow:create"), new Authority("workflow:activate"),
                new Authority("workflow:transition"), new Authority("workflow:read")), true);
    }

    private static final class TestPolicy implements WorkflowAccessPolicy {
        private WorkflowAccessDecision decision = WorkflowAccessDecision.ALLOWED;
        private RuntimeException failure;
        private WorkflowOperation expectedOperation;

        @Override
        public WorkflowAccessDecision forCreation(AuthenticatedPrincipal caller, WorkflowDefinition definition,
                                                  ResourceReference resource) {
            if (!DEFINITION.equals(definition) || !RESOURCE.equals(resource)) {
                return WorkflowAccessDecision.HIDDEN;
            }
            return decision();
        }

        @Override
        public WorkflowAccessDecision forInstance(AuthenticatedPrincipal caller, WorkflowInstance instance,
                                                  WorkflowOperation operation) {
            if (!RESOURCE.equals(instance.resourceReference())
                    || !DEFINITION.definitionId().equals(instance.definitionId())
                    || DEFINITION.definitionVersion() != instance.definitionVersion()
                    || (expectedOperation != null && expectedOperation != operation)) {
                return WorkflowAccessDecision.HIDDEN;
            }
            return decision();
        }

        private WorkflowAccessDecision decision() {
            if (failure != null) {
                throw failure;
            }
            return decision;
        }
    }
}
