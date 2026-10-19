package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.application.authorization.AuthorizedWorkflowService;
import com.tricore.dxos.application.authorization.WorkflowAccessDecision;
import com.tricore.dxos.application.authorization.WorkflowAccessException;
import com.tricore.dxos.application.authorization.WorkflowAccessPolicy;
import com.tricore.dxos.application.authorization.WorkflowAuthorization;
import com.tricore.dxos.application.authorization.WorkflowOperation;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.TransitionResult;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowPersistencePort;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.core.workflow.WorkflowTransition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Spring wiring with the real CurrentPrincipal adapter, authorization and Runtime; mocked ACL
 * and persistence only. JWT contexts are test fixtures, not signature/JWKS or PostgreSQL evidence.
 * No HTTP adapter or production bean registration is introduced.
 */
@SpringJUnitConfig(AuthorizedWorkflowServiceIntegrationTest.Config.class)
@TestPropertySource(properties = "tricore.security.oidc.resource-client-id=tricore-api")
class AuthorizedWorkflowServiceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");
    private static final String ISSUER = "https://issuer.example.test";
    private static final WorkflowDefinition DEFINITION = new WorkflowDefinition(
            "publication", 7, "draft", Set.of("draft", "published"),
            Set.of(new WorkflowTransition("publish", "draft", "published")));
    private static final ResourceReference RESOURCE = new ResourceReference("document", "document-1");
    private static final WorkflowInstance INITIAL = WorkflowInstance.notStarted("instance-1", DEFINITION, RESOURCE, NOW);
    private static final WorkflowInstance ACTIVE = INITIAL.activate(DEFINITION, NOW);

    @Autowired private AuthorizedWorkflowService service;
    @Autowired private WorkflowAccessPolicy policy;
    @Autowired private WorkflowPersistencePort persistence;
    @Autowired private WorkflowRuntime runtime;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        reset(policy, persistence);
        clearInvocations(runtime);
        when(persistence.loadDefinition("publication", 7)).thenReturn(DEFINITION);
        when(persistence.loadInstance("instance-1")).thenReturn(ACTIVE);
        when(policy.forCreation(any(), any(), any())).thenReturn(WorkflowAccessDecision.ALLOWED);
        when(policy.forInstance(any(), any(), any())).thenReturn(WorkflowAccessDecision.ALLOWED);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("unverifiedContexts")
    void unverifiedContextsCannotReadOrWriteThroughTheService(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> service.createInstance("instance-1", "publication", 7, RESOURCE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.activateInstance("instance-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.executeTransition("instance-1", "publish", 0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.loadInstance("instance-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.loadHistory("instance-1")).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(policy, persistence, runtime);
    }

    static Stream<Authentication> unverifiedContexts() {
        return Stream.of(null,
                new AnonymousAuthenticationToken("test-key", "anonymous",
                        List.of(new SimpleGrantedAuthority("workflow:read"))),
                UsernamePasswordAuthenticationToken.authenticated("spoofed-user", "unused",
                        List.of(new SimpleGrantedAuthority("workflow:read"))),
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedPrincipal("spoofed-subject", null, Set.of(new Authority("workflow:read")), true),
                        "unused", List.of(new SimpleGrantedAuthority("workflow:read"))),
                new JwtAuthenticationToken(jwt(List.of("workflow:read"))));
    }

    @Test
    void realRuntimeCreatesOnlyAfterExactDefinitionAndResourceAuthorization() {
        authenticate("workflow:create");
        when(persistence.loadInstance("instance-1"))
                .thenThrow(new WorkflowException(WorkflowErrorCode.INSTANCE_NOT_FOUND));
        when(persistence.createInstance(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowInstance created = service.createInstance("instance-1", "publication", 7, RESOURCE);
        assertThat(created).usingRecursiveComparison().isEqualTo(INITIAL);

        var order = inOrder(policy, runtime, persistence);
        order.verify(persistence).loadDefinition("publication", 7);
        order.verify(policy).forCreation(any(), eq(DEFINITION), eq(RESOURCE));
        order.verify(runtime).createInstance("instance-1", "publication", 7, RESOURCE);
        order.verify(persistence).loadInstance("instance-1");
        order.verify(persistence).loadDefinition("publication", 7);
        order.verify(persistence).createInstance(created);
        order.verifyNoMoreInteractions();
    }

    @Test
    void realRuntimeActivatesOnlyAfterAuthorization() {
        authenticate("workflow:activate");
        when(persistence.loadInstance("instance-1")).thenReturn(INITIAL);
        when(persistence.commitActivation(eq(INITIAL), any())).thenAnswer(invocation -> invocation.getArgument(1));

        WorkflowInstance activated = service.activateInstance("instance-1");
        assertThat(activated).usingRecursiveComparison().isEqualTo(ACTIVE);

        var order = inOrder(policy, runtime, persistence);
        order.verify(persistence).loadInstance("instance-1");
        order.verify(policy).forInstance(any(), eq(INITIAL), eq(WorkflowOperation.ACTIVATE));
        order.verify(runtime).activateInstance("instance-1");
        // Runtime's own snapshot read is also observed by the spy.
        order.verify(runtime).loadInstance("instance-1");
        order.verify(persistence).loadInstance("instance-1");
        order.verify(persistence).loadDefinition("publication", 7);
        order.verify(persistence).commitActivation(INITIAL, activated);
        order.verifyNoMoreInteractions();
    }

    @Test
    void realRuntimePersistsCanonicalVerifiedSubjectAsActor() {
        authenticate("workflow:transition");
        when(persistence.commitTransition(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransitionResult result = service.executeTransition("instance-1", "publish", 0);

        ArgumentCaptor<AuthenticatedPrincipal> caller = ArgumentCaptor.forClass(AuthenticatedPrincipal.class);
        verify(policy).forInstance(caller.capture(), eq(ACTIVE), eq(WorkflowOperation.TRANSITION));
        String expectedActor = "oidc:" + base64(ISSUER) + "." + base64("user-123");
        assertThat(caller.getValue().subjectId()).isEqualTo(expectedActor);
        assertThat(result.record().actorReference().actorId()).isEqualTo(expectedActor);
        assertThat(result.instance().runtimeVersion()).isEqualTo(1);
        assertThat(result.instance().currentState()).isEqualTo("published");
        verify(persistence).commitTransition(result);
    }

    @Test
    void realRuntimeReturnsSnapshotAndHistoryOnlyAfterReadAuthorization() {
        authenticate("workflow:read");
        when(persistence.loadHistory("instance-1")).thenReturn(List.of());

        assertThat(service.loadInstance("instance-1")).isSameAs(ACTIVE);
        assertThat(service.loadHistory("instance-1")).isEmpty();

        verify(policy, times(2)).forInstance(any(), eq(ACTIVE), eq(WorkflowOperation.READ));
        verify(runtime).loadInstance("instance-1");
        verify(runtime).loadHistory("instance-1");
        verify(persistence).loadHistory("instance-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"workflow:create", "workflow:*", "Workflow:read", " workflow:read", "workflow:read "})
    void realIdentityAuthorityMappingCannotGrantAnIncorrectReadAuthority(String authority) {
        authenticate(authority);

        assertThatThrownBy(() -> service.loadHistory("instance-1"))
                .isInstanceOf(WorkflowAccessException.class).hasMessage("FORBIDDEN");
        verifyNoInteractions(runtime);
        verify(persistence).loadInstance("instance-1");
        verifyNoMoreInteractions(persistence);
    }

    @Test
    void aclUnavailabilityBlocksReadsAndWritesEvenWithAllAuthorities() {
        authenticate("workflow:create", "workflow:activate", "workflow:transition", "workflow:read");
        when(policy.forCreation(any(), any(), any())).thenReturn(WorkflowAccessDecision.UNAVAILABLE);
        when(policy.forInstance(any(), any(), any())).thenReturn(WorkflowAccessDecision.UNAVAILABLE);

        assertUnavailable(() -> service.createInstance("instance-1", "publication", 7, RESOURCE));
        assertUnavailable(() -> service.activateInstance("instance-1"));
        assertUnavailable(() -> service.executeTransition("instance-1", "publish", 0));
        assertUnavailable(() -> service.loadInstance("instance-1"));
        assertUnavailable(() -> service.loadHistory("instance-1"));

        verifyNoInteractions(runtime);
        verify(persistence).loadDefinition("publication", 7);
        verify(persistence, times(4)).loadInstance("instance-1");
        verifyNoMoreInteractions(persistence);
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(WorkflowAccessException.class).hasMessage("UNAVAILABLE");
    }

    private static void authenticate(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt(List.of(roles)), List.of(), "display-name-is-not-actor"));
    }

    private static Jwt jwt(List<String> roles) {
        return new Jwt("test-context-only", NOW, NOW.plusSeconds(300), Map.of("alg", "RS256"),
                Map.of("iss", ISSUER, "sub", "user-123",
                        "resource_access", Map.of("tricore-api", Map.of("roles", roles))));
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SpringSecurityCurrentPrincipal.class)
    static class Config {
        @Bean
        WorkflowAccessPolicy policy() {
            return mock(WorkflowAccessPolicy.class);
        }

        @Bean
        WorkflowPersistencePort persistence() {
            return mock(WorkflowPersistencePort.class);
        }

        @Bean
        WorkflowRuntime runtime(WorkflowPersistencePort persistence) {
            return spy(new WorkflowRuntime(persistence, Clock.fixed(NOW, ZoneOffset.UTC)));
        }

        @Bean
        AuthorizedWorkflowService service(CurrentPrincipal principal, WorkflowAccessPolicy policy,
                                          WorkflowPersistencePort persistence, WorkflowRuntime runtime) {
            return new AuthorizedWorkflowService(principal, new WorkflowAuthorization(policy), persistence, runtime);
        }
    }
}
