package com.tricore.dxos.common;

import com.tricore.dxos.request.service.RequestService;
import com.tricore.dxos.request.service.RequestWorkflowService;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.infrastructure.workflow.PostgresWorkflowPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "dxos.workflow.schema=docs_test"
})
@AutoConfigureMockMvc
class OpenApiDisabledTest {
    @Autowired MockMvc mvc;
    @MockitoBean RequestService requestService;
    @MockitoBean RequestWorkflowService requestWorkflowService;
    @MockitoBean WorkflowRuntime workflowRuntime;
    @MockitoBean PostgresWorkflowPersistence workflowPersistence;

    @Test
    void documentationIsAbsentWithoutDocsProfile() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
