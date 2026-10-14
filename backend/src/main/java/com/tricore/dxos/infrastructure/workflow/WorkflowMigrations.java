package com.tricore.dxos.infrastructure.workflow;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.util.Arrays;

/** The supported Workflow migration entry point validates identifiers before SQL placeholder replacement. */
public final class WorkflowMigrations {
    private WorkflowMigrations() {}

    public static FluentConfiguration configure(DataSource source, String schema, String... locations) {
        FluentConfiguration configuration = Flyway.configure().dataSource(source).locations(locations);
        isolate(configuration, new WorkflowSchema(schema));
        return configuration;
    }

    static void isolate(FluentConfiguration configuration, WorkflowSchema schema) {
        if (configuration.getDefaultSchema() != null
                && !schema.name().equals(configuration.getDefaultSchema())) {
            throw new IllegalArgumentException("Flyway default schema mismatch");
        }
        if (configuration.getSchemas().length != 0
                && !Arrays.equals(configuration.getSchemas(), new String[]{schema.name()})) {
            throw new IllegalArgumentException("Flyway managed schemas mismatch");
        }
        if (!configuration.isExecuteInTransaction()) {
            throw new IllegalArgumentException("Workflow migrations require transactions");
        }
        ClassLoader migrationLoader = configuration.getClassLoader();
        Callback boundary = new Callback() {
            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.BEFORE_VALIDATE || event == Event.BEFORE_MIGRATE
                        || event == Event.BEFORE_EACH_MIGRATE || event == Event.BEFORE_CLEAN
                        || event == Event.BEFORE_REPAIR || event == Event.BEFORE_BASELINE;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                return true;
            }

            @Override
            public void handle(Event event, Context context) {
                if (event == Event.BEFORE_CLEAN || event == Event.BEFORE_REPAIR || event == Event.BEFORE_BASELINE) {
                    throw new IllegalArgumentException("Workflow migration operation rejected");
                }
                var actual = context.getConfiguration();
                verifyConfiguration(actual, schema, this, migrationLoader);
                try {
                    WorkflowEventTriggerGuard.REVIEWED.verify(context.getConnection(), schema.name());
                    WorkflowEventTriggerGuard.verifyMigrations(actual, context.getConnection());
                    if (event == Event.BEFORE_EACH_MIGRATE) {
                        String script = context.getMigrationInfo().getScript();
                        if (!WorkflowEventTriggerGuard.MANIFEST.path("migrations").has(script)
                                && !(WorkflowEventTriggerGuard.emptyLocal(context.getConnection())
                                && java.util.Set.of("V1__create_requests.sql", "V2__add_request_workflow.sql").contains(script))) {
                            throw new IllegalArgumentException("Workflow migration script rejected");
                        }
                    }
                } catch (java.sql.SQLException failure) {
                    throw new IllegalArgumentException("Workflow migration connection rejected");
                }
            }

            @Override
            public String getCallbackName() {
                return "workflow-schema-isolation";
            }
        };
        configuration.dataSource(schema.guard(configuration.getDataSource(), connection -> {
                    verifyConfiguration(configuration, schema, boundary, migrationLoader);
                    try {
                        WorkflowEventTriggerGuard.verifyMigrations(configuration, connection);
                    } catch (java.sql.SQLException failure) {
                        throw new IllegalArgumentException("Workflow migration scope rejected");
                    }
                }))
                .defaultSchema(schema.name()).schemas(schema.name()).createSchemas(false)
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false)
                .executeInTransaction(true).skipDefaultCallbacks(true).callbacks(boundary);
    }

    private static void verifyConfiguration(Configuration configuration, WorkflowSchema schema,
                                            Callback boundary, ClassLoader migrationLoader) {
        if (!schema.name().equals(configuration.getDefaultSchema())
                || !Arrays.equals(configuration.getSchemas(), new String[]{schema.name()})
                || configuration.isCreateSchemas() || !configuration.isCleanDisabled()
                || configuration.isBaselineOnMigrate() || configuration.isOutOfOrder()
                || !configuration.isExecuteInTransaction()
                || !"flyway_schema_history".equals(configuration.getTable())
                || !configuration.isPlaceholderReplacement() || !configuration.isSkipDefaultCallbacks()
                || configuration.getCallbacks().length != 1 || configuration.getCallbacks()[0] != boundary
                || configuration.getResolvers().length != 0 || configuration.isSkipDefaultResolvers()
                || configuration.getResourceProvider() != null || configuration.getJavaMigrationClassProvider() != null
                || configuration.getJavaMigrations().length != 0 || configuration.getWorkingDirectory() != null
                || configuration.getClassLoader() != migrationLoader
                || !java.nio.charset.StandardCharsets.UTF_8.equals(configuration.getEncoding())
                || configuration.isDetectEncoding()
                || (configuration.getInitSql() != null && !configuration.getInitSql().isBlank())
                || !configuration.getPlaceholders().isEmpty()
                || !"${".equals(configuration.getPlaceholderPrefix()) || !"}".equals(configuration.getPlaceholderSuffix())) {
            throw new IllegalArgumentException("Workflow migration configuration rejected");
        }
    }
}
