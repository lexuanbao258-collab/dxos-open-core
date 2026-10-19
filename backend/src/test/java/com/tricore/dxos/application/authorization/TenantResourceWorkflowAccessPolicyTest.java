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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Stage 5 pure policy tests: the reader is a synthetic authoritative-source double, NOT PostgreSQL. */
class TenantResourceWorkflowAccessPolicyTest {
    private static final String ACTOR = "oidc:issuer.subject";
    private static final ResourceReference RESOURCE = new ResourceReference("document", "document-a");
    private static final ResourceReference OTHER = new ResourceReference("document", "document-b");
    private static final WorkflowDefinition DEFINITION = new WorkflowDefinition(
            "publication", 7, "draft", Set.of("draft"), Set.of());
    private static final WorkflowInstance INSTANCE = WorkflowInstance.notStarted(
            "instance-a", DEFINITION, RESOURCE, Instant.parse("2026-10-19T00:00:00Z"));
    private static final WorkflowAclSnapshotReader.DefinitionBinding BINDING =
            new WorkflowAclSnapshotReader.DefinitionBinding("publication", 7);

    private final WorkflowAclSnapshotReader reader = mock(WorkflowAclSnapshotReader.class);
    private final WorkflowAuthorization auth =
            new WorkflowAuthorization(new TenantResourceWorkflowAccessPolicy(reader));

    @Test
    void activeMemberWithExactGrantAndBindingMayCreateUsingOneSnapshot() {
        allow(facts(RESOURCE, ACTOR, true, true, false,
                Set.of(WorkflowOperation.CREATE), Set.of(BINDING)));
        assertThatCode(() -> auth.authorizeCreation(caller("workflow:create"), DEFINITION, RESOURCE))
                .doesNotThrowAnyException();
        verify(reader, times(1)).read(RESOURCE, ACTOR);
    }

    @Test
    void ownerVisibilityDoesNotImplicitlyGrantCreate() {
        allow(facts(RESOURCE, ACTOR, true, true, true, Set.of(), Set.of(BINDING)));
        denyCreate(WorkflowAccessDecision.FORBIDDEN, "workflow:create");
    }

    @Test
    void differentExplicitGrantGivesVisibilityButNotRequestedPermission() {
        allow(facts(RESOURCE, ACTOR, true, true, false,
                Set.of(WorkflowOperation.READ), Set.of(BINDING)));
        denyCreate(WorkflowAccessDecision.FORBIDDEN, "workflow:create");
    }

    @Test
    void exactResourceGrantCannotReplaceMissingVerifiedOperationAuthority() {
        allow(facts(RESOURCE, ACTOR, true, true, true,
                Set.of(WorkflowOperation.CREATE), Set.of(BINDING)));
        denyCreate(WorkflowAccessDecision.FORBIDDEN, "workflow:read");
    }

    @ParameterizedTest
    @MethodSource("hiddenCreationCases")
    void hiddenAndCrossTenantResourceScenariosCannotCreate(
            boolean active, boolean member, boolean owner, Set<WorkflowOperation> grants) {
        allow(facts(RESOURCE, ACTOR, active, member, owner, grants, Set.of(BINDING)));
        denyCreate(WorkflowAccessDecision.HIDDEN, "workflow:create");
    }

    static Stream<Arguments> hiddenCreationCases() {
        return Stream.of(
                Arguments.of(false, true, true, Set.of(WorkflowOperation.CREATE)), // tombstone
                Arguments.of(true, false, true, Set.of(WorkflowOperation.CREATE)), // foreign tenant/no membership
                Arguments.of(true, true, false, Set.of()), // member but cannot see
                Arguments.of(false, false, false, Set.of()) // missing active tenant/resource
        );
    }

    @Test
    void unknownOrUnmappedLegacyV3ResourceIsHidden() {
        when(reader.read(RESOURCE, ACTOR)).thenReturn(Optional.empty());
        denyCreate(WorkflowAccessDecision.HIDDEN, "workflow:create");
    }

    @Test
    void missingExactDefinitionBindingIsHiddenEvenWithCreateGrant() {
        allow(facts(RESOURCE, ACTOR, true, true, true, Set.of(WorkflowOperation.CREATE), Set.of()));
        denyCreate(WorkflowAccessDecision.HIDDEN, "workflow:create");
        verify(reader).read(RESOURCE, ACTOR);
    }

    @Test
    void otherDefinitionVersionOrIdCannotSubstituteForExactBinding() {
        allow(facts(RESOURCE, ACTOR, true, true, true, Set.of(WorkflowOperation.CREATE),
                Set.of(new WorkflowAclSnapshotReader.DefinitionBinding("publication", 8),
                        new WorkflowAclSnapshotReader.DefinitionBinding("other", 7))));
        denyCreate(WorkflowAccessDecision.HIDDEN, "workflow:create");
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowOperation.class, names = {"ACTIVATE", "TRANSITION", "READ"})
    void existingInstanceIsNotRevokedJustByDefinitionBindingRemoval(WorkflowOperation operation) {
        allow(facts(RESOURCE, ACTOR, true, true, false, Set.of(operation), Set.of()));
        assertThatCode(() -> auth.authorizeInstance(caller(operation.requiredAuthority().value()),
                INSTANCE, operation)).doesNotThrowAnyException();
        verify(reader).read(RESOURCE, ACTOR);
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowOperation.class, names = {"ACTIVATE", "TRANSITION", "READ"})
    void activeOwnerWithNoOperationGrantIsForbiddenForExistingInstance(WorkflowOperation operation) {
        allow(facts(RESOURCE, ACTOR, true, true, true, Set.of(), Set.of(BINDING)));
        denyInstance(operation, WorkflowAccessDecision.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowOperation.class, names = {"ACTIVATE", "TRANSITION", "READ"})
    void crossTenantOrRevokedMembershipIsHiddenForInstance(WorkflowOperation operation) {
        allow(facts(RESOURCE, ACTOR, true, false, true, Set.of(operation), Set.of(BINDING)));
        denyInstance(operation, WorkflowAccessDecision.HIDDEN);
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowOperation.class, names = {"ACTIVATE", "TRANSITION", "READ"})
    void tombstonedResourceIsHiddenEvenWithExplicitGrant(WorkflowOperation operation) {
        allow(facts(RESOURCE, ACTOR, false, true, true, Set.of(operation), Set.of(BINDING)));
        denyInstance(operation, WorkflowAccessDecision.HIDDEN);
    }

    @Test
    void resourceWithoutOwnerButWithReadGrantIsVisibleAndReadable() {
        allow(facts(RESOURCE, ACTOR, true, true, false, Set.of(WorkflowOperation.READ), Set.of()));
        assertThatCode(() -> auth.authorizeInstance(caller("workflow:read"),
                INSTANCE, WorkflowOperation.READ)).doesNotThrowAnyException();
    }

    @Test
    void actorIsAlwaysLookedUpUsingCanonicalVerifiedSubject() {
        allow(facts(RESOURCE, ACTOR, true, true, true, Set.of(WorkflowOperation.READ), Set.of()));
        auth.authorizeInstance(caller("workflow:read"), INSTANCE, WorkflowOperation.READ);
        verify(reader).read(RESOURCE, ACTOR);
        verifyNoMoreInteractions(reader);
    }

    @Test
    void wrongResourceEchoIsUnavailableRatherThanLeakingAnotherResource() {
        allow(facts(OTHER, ACTOR, true, true, true, Set.of(WorkflowOperation.READ), Set.of()));
        denyInstance(WorkflowOperation.READ, WorkflowAccessDecision.UNAVAILABLE);
    }

    @Test
    void wrongActorEchoIsUnavailableRatherThanUsingAnotherUsersPermissions() {
        allow(facts(RESOURCE, "oidc:someone.else", true, true, true,
                Set.of(WorkflowOperation.READ), Set.of()));
        denyInstance(WorkflowOperation.READ, WorkflowAccessDecision.UNAVAILABLE);
    }

    @Test
    void nullOrThrowingSourceFailsClosedWithoutProviderDetails() {
        when(reader.read(RESOURCE, ACTOR)).thenReturn(null);
        denyInstance(WorkflowOperation.READ, WorkflowAccessDecision.UNAVAILABLE);
        reset(reader);
        when(reader.read(RESOURCE, ACTOR)).thenThrow(new IllegalStateException("secret SQL diagnostic"));
        denyInstance(WorkflowOperation.READ, WorkflowAccessDecision.UNAVAILABLE);
    }

    @Test
    void sourceFailureCannotCreateEvenWithAllAuthorityAndGrant() {
        when(reader.read(RESOURCE, ACTOR)).thenThrow(new IllegalStateException("private tenant mapping"));
        denyCreate(WorkflowAccessDecision.UNAVAILABLE, "workflow:create");
    }

    @Test
    void rejectsCreateAsInstanceOperation() {
        assertThatThrownBy(() -> new TenantResourceWorkflowAccessPolicy(reader)
                .forInstance(caller("workflow:create"), INSTANCE, WorkflowOperation.CREATE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reader);
    }

    private static AuthenticatedPrincipal caller(String authority) {
        return new AuthenticatedPrincipal(ACTOR, "not-an-authority", Set.of(new Authority(authority)), true);
    }

    private static WorkflowAclSnapshotReader.ResourceAclSnapshot facts(
            ResourceReference resource, String actor, boolean active, boolean member,
            boolean owner, Set<WorkflowOperation> grants,
            Set<WorkflowAclSnapshotReader.DefinitionBinding> bindings) {
        return new WorkflowAclSnapshotReader.ResourceAclSnapshot(
                resource, actor, "tenant-a", active, member, owner, grants, bindings);
    }

    private void allow(WorkflowAclSnapshotReader.ResourceAclSnapshot facts) {
        when(reader.read(RESOURCE, ACTOR)).thenReturn(Optional.of(facts));
    }

    private void denyCreate(WorkflowAccessDecision decision, String authority) {
        assertThatThrownBy(() -> auth.authorizeCreation(caller(authority), DEFINITION, RESOURCE))
                .isInstanceOf(WorkflowAccessException.class).hasMessage(decision.name()).hasNoCause();
    }

    private void denyInstance(WorkflowOperation operation, WorkflowAccessDecision decision) {
        assertThatThrownBy(() -> auth.authorizeInstance(caller(operation.requiredAuthority().value()),
                INSTANCE, operation)).isInstanceOf(WorkflowAccessException.class)
                .hasMessage(decision.name()).hasNoCause();
    }
}
