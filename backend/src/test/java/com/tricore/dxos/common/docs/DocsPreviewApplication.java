package com.tricore.dxos.common.docs;

import com.tricore.dxos.common.health.HealthController;
import com.tricore.dxos.request.controller.RequestController;
import com.tricore.dxos.request.controller.RequestWorkflowController;
import com.tricore.dxos.request.service.RequestService;
import com.tricore.dxos.request.service.RequestWorkflowService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Test-classpath ONLY preview: actual registered Health/Request MVC controllers, no persistent services.
 * This is NOT the production DxosApplication and must never be used to validate end-to-end auth or DB.
 * All application API requests are blocked; only documentation resources are served on IPv4 loopback.
 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@Import({
        HealthController.class,
        RequestController.class,
        RequestWorkflowController.class,
        LocalApiDocumentationConfiguration.class
})
public class DocsPreviewApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DocsPreviewApplication.class);
        application.setAdditionalProfiles("docs-local");
        application.run(args);
    }

    @Bean
    RequestService previewRequestService() {
        return Mockito.mock(RequestService.class);
    }

    @Bean
    RequestWorkflowService previewRequestWorkflowService() {
        return Mockito.mock(RequestWorkflowService.class);
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> previewLoopbackOnly() {
        // Force loopback even if SERVER_ADDRESS or CLI properties try to override it.
        return factory -> {
            try {
                factory.setAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            } catch (java.net.UnknownHostException impossible) {
                throw new IllegalStateException("Cannot configure IPv4 loopback", impossible);
            }
        };
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> blockPreviewBusinessApis() {
        OncePerRequestFilter blocker = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String uri = request.getRequestURI();
                if (uri.startsWith("/api/") || uri.startsWith("/actuator/")) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "Documentation preview: API execution is disabled");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(blocker);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
