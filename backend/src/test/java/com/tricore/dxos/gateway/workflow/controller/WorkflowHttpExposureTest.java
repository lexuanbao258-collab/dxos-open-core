package com.tricore.dxos.gateway.workflow.controller;

import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.request.service.RequestService;
import com.tricore.dxos.request.service.RequestWorkflowService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests disabled production registration, not OIDC verification or an actual security filter chain. */
@WebMvcTest
class WorkflowHttpExposureTest {
    private static final String BASE = "/api/v1/workflow-instances";
    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;
    @MockitoBean private WorkflowRuntime runtime;
    @MockitoBean private RequestService requestService;
    @MockitoBean private RequestWorkflowService requestWorkflowService;

    @ParameterizedTest
    @MethodSource("routes")
    void productionComponentScanningDoesNotExposeWorkflowEvenWithARuntimeBean(String method, String suffix) throws Exception {
        assertThat(context.getBeansOfType(WorkflowController.class)).isEmpty();
        assertThat(mappings.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .anyMatch(path -> path.startsWith(BASE))).isFalse();
        mvc.perform(request(HttpMethod.valueOf(method), BASE + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("X-User", "attacker").header("X-Role", "admin")
                        .header("Authorization", "Bearer unverified"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(runtime);
    }

    static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("POST", ""),
                Arguments.of("POST", "/instance-1/activate"),
                Arguments.of("POST", "/instance-1/transitions"),
                Arguments.of("GET", "/instance-1"),
                Arguments.of("GET", "/instance-1/history"));
    }
}
