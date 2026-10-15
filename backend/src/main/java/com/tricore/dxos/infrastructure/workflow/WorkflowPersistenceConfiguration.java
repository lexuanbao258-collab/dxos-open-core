package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.core.workflow.WorkflowPersistencePort;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class WorkflowPersistenceConfiguration {
    @Bean
    FlywayConfigurationCustomizer workflowMigrationIsolation(@Value("${dxos.workflow.schema}") String name) {
        WorkflowSchema schema = new WorkflowSchema(name);
        return configuration -> WorkflowMigrations.isolate(configuration, schema);
    }

    @Bean
    WorkflowRuntime workflowRuntime(WorkflowPersistencePort persistence) {
        return new WorkflowRuntime(persistence, Clock.systemUTC());
    }
}
