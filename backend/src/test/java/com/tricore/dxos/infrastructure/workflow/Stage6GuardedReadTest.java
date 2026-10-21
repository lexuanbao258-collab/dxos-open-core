package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.application.authorization.WorkflowAccessDecision;
import com.tricore.dxos.application.authorization.WorkflowAccessException;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import org.junit.jupiter.api.Test;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.concurrent.Executor;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class Stage6GuardedReadTest {
    private final DataSource source = mock(DataSource.class);
    private final CurrentPrincipal principal = mock(CurrentPrincipal.class);
    private final PostgresWorkflowPersistence persistence = new PostgresWorkflowPersistence(source, "s6_read_test");

    @Test
    void d3ScopeRequiresVerificationAndRejectsForeignTargetsWithoutSql() {
        var failure = new IllegalStateException("unverified");
        when(principal.requireVerified()).thenThrow(failure);
        assertThatThrownBy(() -> persistence.forStage6(principal,
                com.tricore.dxos.application.authorization.WorkflowOperation.READ,"one",null,null,null,null,null)).isSameAs(failure);
        reset(principal);
        verified();
        var scope = persistence.forStage6(principal,
                com.tricore.dxos.application.authorization.WorkflowOperation.READ,"one",null,null,null,null,null);
        assertUnavailable(() -> scope.loadInstance("two"));
        assertThatThrownBy(() -> scope.loadInstance(null)).isInstanceOf(IllegalArgumentException.class);
        assertUnavailable(() -> scope.loadDefinition("publication",7));
        assertUnavailable(() -> scope.commitTransition(null));
        verifyNoInteractions(source);
    }

    @Test
    void unverifiedOrNullPrincipalNeverOpensConnection() {
        var failure = new IllegalStateException("verification failed");
        when(principal.requireVerified()).thenThrow(failure).thenReturn(null);
        assertThatThrownBy(() -> persistence.forRead(principal, "one")).isSameAs(failure);
        assertThatThrownBy(() -> persistence.forRead(principal, "one")).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(source);
    }

    @Test
    void scopeRejectsOtherInstancesAndEveryNonReadMethodWithoutDatabaseAccess() {
        verified();
        var read = persistence.forRead(principal, "one");
        assertUnavailable(() -> read.loadInstance("two"));
        assertUnavailable(() -> read.loadHistory("two"));
        assertUnavailable(() -> read.loadDefinition("publication", 7));
        assertUnavailable(() -> read.createInstance(null));
        assertUnavailable(() -> read.commitActivation(null, null));
        assertUnavailable(() -> read.commitTransition(null));
        var scoped = (PostgresWorkflowPersistence) read;
        assertUnavailable(() -> scoped.provisionDefinition(null));
        assertUnavailable(() -> scoped.forRead(principal, "two"));
        verify(principal).requireVerified();
        verifyNoInteractions(source);
    }

    @Test
    void providerFailureIsSanitizedForBothReadMethods() throws Exception {
        verified();
        when(source.getConnection()).thenThrow(new SQLException("private database details"));
        var read = persistence.forRead(principal, "one");
        assertUnavailable(() -> read.loadInstance("one"));
        assertUnavailable(() -> read.loadHistory("one"));
        verify(principal).requireVerified();
    }

    @Test
    void providerRuntimeFailureIsSanitizedWithoutRetry() throws Exception {
        verified();
        when(source.getConnection()).thenThrow(new IllegalStateException("private pool failure"));
        var read = persistence.forRead(principal, "one");
        assertUnavailable(() -> read.loadInstance("one"));
        assertUnavailable(() -> read.loadHistory("one"));
        verify(source, times(2)).getConnection();
        verify(principal).requireVerified();
    }

    @Test
    void protectedCompositionNeverCallsLegacyReadsOrFallsBackAfterFailure() throws Exception {
        verified();
        var legacy = spy(persistence);
        var service = new Stage6GuardedReadService(legacy, principal);
        when(source.getConnection()).thenThrow(new SQLException("private connection failure"));
        assertUnavailable(() -> service.loadInstance("one"));
        assertUnavailable(() -> service.loadHistory("two"));
        verify(legacy, never()).loadInstance(anyString());
        verify(legacy, never()).loadHistory(anyString());
        verify(legacy).forRead(principal, "one");
        verify(legacy).forRead(principal, "two");
        verify(principal, times(2)).requireVerified();
        verify(source, times(2)).getConnection();
        when(principal.requireVerified()).thenThrow(new IllegalStateException("unverified"));
        assertThatThrownBy(() -> service.loadInstance("one")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.loadHistory("one")).isInstanceOf(IllegalStateException.class);
        verify(source, times(2)).getConnection();
    }

    @Test
    void eachProtectedCallGetsAFreshImmutableActorAndTargetScope() throws Exception {
        verified();
        var legacy = spy(persistence);
        var service = new Stage6GuardedReadService(legacy, principal);
        when(source.getConnection()).thenThrow(new SQLException("offline"));
        var actorOne = persistence.forRead(principal, "one");
        when(principal.requireVerified()).thenReturn(new AuthenticatedPrincipal(
                "actor-two", "test", Set.of(), true));
        assertUnavailable(() -> service.loadHistory("two"));
        assertUnavailable(() -> actorOne.loadHistory("two"));
        verify(source, times(1)).getConnection();
        verify(legacy).forRead(principal, "two");
    }

    @Test
    void unsupportedOrIneffectiveNetworkTimeoutFailsBeforeAnySqlAndDiscards() throws Exception {
        verified();
        for (boolean unsupported : new boolean[]{false, true}) {
            Connection connection = mock(Connection.class);
            when(source.getConnection()).thenReturn(connection);
            if (unsupported) doThrow(new SQLException("private unsupported timeout"))
                    .when(connection).setNetworkTimeout(any(Executor.class), anyInt());
            assertUnavailable(() -> persistence.forRead(principal, "one").loadInstance("one"));
            verify(connection).abort(any(Executor.class));
            verify(connection).close();
            verify(connection, never()).createStatement();
            verify(connection, never()).prepareStatement(anyString());
            verify(connection, never()).commit();
            verify(connection, never()).rollback();
        }
    }

    @Test
    void hikariEvictionStillRunsWhenJdbcAbortFails() throws Exception {
        verified();
        HikariDataSource pool = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        when(pool.getConnection()).thenReturn(connection);
        doThrow(new SQLException("private abort failure")).when(connection).abort(any(Executor.class));
        var service = new Stage6GuardedReadService(new PostgresWorkflowPersistence(pool, "s6_read_test"), principal);
        assertUnavailable(() -> service.loadInstance("one"));
        verify(pool).evictConnection(connection);
        verify(connection).close();
        verify(connection, never()).createStatement();
    }

    private void verified() {
        when(principal.requireVerified()).thenReturn(new AuthenticatedPrincipal(
                "oidc:test.actor", "test", Set.of(new Authority("workflow:read")), true));
    }

    private void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(WorkflowAccessException.class,
                failure -> org.assertj.core.api.Assertions.assertThat(failure.decision())
                        .isEqualTo(WorkflowAccessDecision.UNAVAILABLE))
                .hasMessage("UNAVAILABLE").hasNoCause();
    }
}
