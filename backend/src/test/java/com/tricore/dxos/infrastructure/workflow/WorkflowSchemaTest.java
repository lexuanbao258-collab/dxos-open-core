package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WorkflowSchemaTest {
    @Test void approvedBundleAcceptsWindowsCheckoutLineEndings(@TempDir Path bundle) throws Exception {
        for (String name : List.of("V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql")) {
            String sql = Files.readString(Path.of("src/main/resources/db/migration", name));
            Files.writeString(bundle.resolve(name), sql.replace("\r\n", "\n").replace("\n", "\r\n"));
        }
        new WorkflowSmokeRunner(mock(DataSource.class), mock(DataSource.class), bundle);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "public", "extensions", "information_schema", "pg_catalog",
            "pg_private", "bad\"; DROP SCHEMA public; --", "bad'; --", "UPPER", "a,b"})
    void rejectsUnsafeIdentifiersBeforeOpeningAnyConnection(String name) {
        assertThatThrownBy(() -> new PostgresWorkflowPersistence(mock(DataSource.class), name))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowMigrations.configure(mock(DataSource.class), name, "classpath:db/migration"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/test?currentSchema=public",
            "jdbc:postgresql://localhost:5432/test?currentSchema=private,public",
            "jdbc:postgresql://localhost:5432/test?currentSchema=private&currentSchema=public",
            "jdbc:postgresql://localhost:5432/test?options=-c%20search_path%3Dpublic",
            "jdbc:postgresql://localhost:5432/test?password=secret",
            "jdbc:postgresql://user:secret@localhost:5432/test"})
    void rejectsOverridesAndSecretsInJdbcUrls(String url) {
        assertThatThrownBy(() -> new WorkflowSchema("private").validateUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicationRejectsPublicSchemaBeforeOpeningAConnection() {
        new ApplicationContextRunner()
                .withUserConfiguration(PostgresWorkflowPersistence.class)
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("dxos.workflow.schema=public")
                .run(context -> assertThat(context).hasFailed());
    }
}
