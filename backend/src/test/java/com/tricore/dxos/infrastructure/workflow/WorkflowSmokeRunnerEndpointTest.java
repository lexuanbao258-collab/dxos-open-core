package com.tricore.dxos.infrastructure.workflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class WorkflowSmokeRunnerEndpointTest {
    private static final String REF = "zsfctpnitkdztlfhzqgw";

    @Test
    void cloudAcceptsOnlyTheExactDirectEndpoint() {
        assertDoesNotThrow(() -> WorkflowSmokeRunner.requireEndpoint(
                "jdbc:postgresql://db." + REF + ".supabase.co:5432/postgres", false, REF));
        for (String url : new String[] {
                "jdbc:postgresql://aws-0-region.pooler.supabase.com:5432/postgres",
                "jdbc:postgresql://db.otherproject.supabase.co:5432/postgres",
                "jdbc:postgresql://db." + REF + ".supabase.co:6543/postgres",
                "jdbc:postgresql://db." + REF + ".supabase.co:5432/other",
                "jdbc:postgresql://db." + REF + ".supabase.co:5432/postgres?host=other",
                "jdbc:postgresql://db." + REF + ".supabase.co:5432/postgres#other"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> WorkflowSmokeRunner.requireEndpoint(url, false, REF));
        }
        assertThrows(IllegalArgumentException.class, () -> WorkflowSmokeRunner.requireEndpoint(
                "jdbc:postgresql://db." + REF + ".supabase.co:5432/postgres", false, "wrong.ref"));
    }

    @Test
    void disposableLocalEndpointRemainsAvailable() {
        assertDoesNotThrow(() -> WorkflowSmokeRunner.requireEndpoint(
                "jdbc:postgresql://localhost:55425/postgres", true, ""));
    }

    @Test
    void preflightRejectsSetRoleInsteadOfARealLogin() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(rows);
        when(rows.getString(1)).thenReturn("on");
        when(rows.getString(2)).thenReturn("postgres");
        when(rows.getString(3)).thenReturn("dxos_wf25_migrator");

        assertThrows(IllegalArgumentException.class, () -> WorkflowSmokeRunner.readConnection(source));
        verify(connection).close();
    }
}
