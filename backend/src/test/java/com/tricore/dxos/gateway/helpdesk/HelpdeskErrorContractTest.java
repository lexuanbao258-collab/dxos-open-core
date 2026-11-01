package com.tricore.dxos.gateway.helpdesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.application.authorization.WorkflowAccessDecision;
import com.tricore.dxos.application.authorization.WorkflowAccessException;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.infrastructure.workflow.HelpdeskService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HelpdeskController.class, properties = "dxos.helpdesk.enabled=true")
@AutoConfigureMockMvc(addFilters = false)
class HelpdeskErrorContractTest {
    private static final String BASE = "/api/v1/mvp/requests";
    private static final UUID ID = UUID.fromString("3e19b6a4-8b83-411a-92a8-bac24851ae2a");
    private static final String NOT_FOUND = """
            {"status":404,"code":"NOT_FOUND","message":"Workflow resource not found","errors":{}}
            """;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private HelpdeskService service;

    static Stream<Arguments> resourceRoutes() {
        return Stream.of("", "/history", "/triage", "/assign", "/start", "/resolve", "/confirm")
                .flatMap(suffix -> Stream.of("", "/portal").map(context -> Arguments.of(suffix, context)));
    }

    @ParameterizedTest
    @MethodSource("resourceRoutes")
    void hiddenAndMissingHaveIdenticalSafeHttpContracts(String suffix, String context) throws Exception {
        failService(new WorkflowAccessException(WorkflowAccessDecision.HIDDEN));
        var hidden = notFound(suffix, context);
        for (var code : List.of(WorkflowErrorCode.INSTANCE_NOT_FOUND, WorkflowErrorCode.DEFINITION_NOT_FOUND)) {
            failService(new WorkflowException(code));
            var missing = notFound(suffix, context);
            assertThat(missing.getContentAsByteArray()).isEqualTo(hidden.getContentAsByteArray());
            assertThat(missing.getContentType()).isEqualTo(hidden.getContentType());
        }
    }

    @ParameterizedTest
    @CsvSource({"VERSION_CONFLICT,409", "COMMIT_OUTCOME_UNKNOWN,500", "OPERATION_FAILURE,500"})
    void otherWorkflowErrorsKeepTheirCodesAndDoNotRetry(WorkflowErrorCode code, int status) throws Exception {
        failService(new WorkflowException(code));
        mvc.perform(request("/resolve", ""))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.name()));
        assertThat(mockingDetails(service).getInvocations()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"FORBIDDEN,403,FORBIDDEN", "UNAVAILABLE,503,AUTHORIZATION_UNAVAILABLE"})
    void otherAccessDenialsKeepTheirCodesAndDoNotRetry(WorkflowAccessDecision decision, int status, String code)
            throws Exception {
        failService(new WorkflowAccessException(decision));
        mvc.perform(request("/resolve", ""))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
        assertThat(mockingDetails(service).getInvocations()).hasSize(1);
    }

    private MockHttpServletResponse notFound(String suffix, String context) throws Exception {
        var response = mvc.perform(request(suffix, context))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workflow resource not found"))
                .andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString())).isEqualTo(json.readTree(NOT_FOUND));
        assertThat(mockingDetails(service).getInvocations()).hasSize(1);
        return response;
    }

    private MockHttpServletRequestBuilder request(String suffix, String context) {
        String uri = context + BASE + "/" + ID + suffix;
        if (suffix.isEmpty() || suffix.equals("/history")) return get(uri).contextPath(context);
        String body = switch (suffix) {
            case "/assign" -> "{\"assigneeId\":\"worker\",\"expectedAssignmentVersion\":0}";
            case "/resolve" -> "{\"expectedVersion\":0,\"resolution\":\"Done\"}";
            default -> "{\"expectedVersion\":0}";
        };
        return post(uri).contextPath(context).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // These mocks verify HTTP projection/no retry; real no-write snapshots remain in the PostgreSQL suite.
    private void failService(RuntimeException failure) {
        reset(service);
        doThrow(failure).when(service).get(ID);
        doThrow(failure).when(service).history(ID);
        doThrow(failure).when(service).assign(eq(ID), anyString(), anyLong());
        doThrow(failure).when(service).transition(eq(ID), anyString(), anyLong(), nullable(String.class));
    }
}
