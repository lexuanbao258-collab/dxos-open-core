package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dxos.helpdesk.enabled", havingValue = "true")
public class HelpdeskConfiguration {
    @Bean
    HelpdeskService helpdeskService(CurrentPrincipal principal,
            @Value("${dxos.helpdesk.jdbc-url}") String url,
            @Value("${dxos.helpdesk.username}") String username,
            @Value("${dxos.helpdesk.password}") String password,
            @Value("${dxos.helpdesk.schema}") String schema,
            @Value("${dxos.helpdesk.tenant}") String tenant,
            @Value("${server.address:}") String address) {
        requireDisposable(url, username, address);
        var database = new DriverManagerDataSource(url, username, password);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", new WorkflowSchema(schema).name() + ",pg_catalog,pg_temp");
        database.setConnectionProperties(properties);
        return new HelpdeskService(new PostgresWorkflowPersistence(database, schema), principal, schema, tenant, Clock.systemUTC());
    }

    static void requireDisposable(String url, String username, String address) {
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/helpdesk_disposable_[a-zA-Z0-9_]+")
                || !username.matches("helpdesk_runtime_[a-z0-9_]+") || !address.equals("127.0.0.1")) {
            throw new IllegalArgumentException("Helpdesk candidate requires loopback HTTP and a dedicated disposable database/runtime login");
        }
    }
}
