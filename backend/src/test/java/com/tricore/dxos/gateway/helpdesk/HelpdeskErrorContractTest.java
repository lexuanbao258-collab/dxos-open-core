package com.tricore.dxos.gateway.helpdesk;

import com.tricore.dxos.application.authorization.WorkflowAccessDecision;
import com.tricore.dxos.application.authorization.WorkflowAccessException;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.gateway.workflow.controller.WorkflowController;
import com.tricore.dxos.gateway.workflow.controller.WorkflowExceptionHandler;
import com.tricore.dxos.infrastructure.workflow.HelpdeskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HelpdeskErrorContractTest {
    @RestController
    static class GenericController extends WorkflowController {
        GenericController(WorkflowRuntime runtime) { super(runtime); }
    }

    private static final UUID ID = UUID.fromString("f3bf54b7-fdab-4b45-85dd-94cbd8587a9d");

    @ParameterizedTest
    @EnumSource(value = WorkflowErrorCode.class, names = {"DEFINITION_NOT_FOUND", "INSTANCE_NOT_FOUND"})
    void genericKeepsItsBaselineContractWhileHelpdeskHidesMissingDetails(WorkflowErrorCode code) throws Exception {
        WorkflowRuntime runtime = mock(WorkflowRuntime.class);
        HelpdeskService service = mock(HelpdeskService.class);
        MockMvc generic = MockMvcBuilders.standaloneSetup(new GenericController(runtime))
                .setControllerAdvice(new WorkflowExceptionHandler()).build();
        MockMvc helpdesk = MockMvcBuilders.standaloneSetup(new HelpdeskController(service))
                .setControllerAdvice(new HelpdeskExceptionHandler()).build();

        when(runtime.loadInstance("instance-1")).thenThrow(new WorkflowException(code));
        when(service.get(ID)).thenThrow(new WorkflowException(code));

        generic.perform(get("/api/v1/workflow-instances/instance-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.message").value(code == WorkflowErrorCode.DEFINITION_NOT_FOUND
                        ? "Workflow definition version not found" : "Workflow instance not found"));
        helpdesk.perform(get("/api/v1/mvp/requests/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workflow resource not found"));

        verify(runtime, times(1)).loadInstance("instance-1");
        verify(service, times(1)).get(ID);
        verifyNoMoreInteractions(runtime, service);
    }

    @Test
    void helpdeskHiddenAndMissingRemainIndistinguishableWithoutWrites() throws Exception {
        HelpdeskService service = mock(HelpdeskService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HelpdeskController(service))
                .setControllerAdvice(new HelpdeskExceptionHandler()).build();

        when(service.get(ID)).thenThrow(new WorkflowAccessException(WorkflowAccessDecision.HIDDEN));
        String hidden = mvc.perform(get("/api/v1/mvp/requests/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        reset(service);
        when(service.get(ID)).thenThrow(new WorkflowException(WorkflowErrorCode.INSTANCE_NOT_FOUND));
        mvc.perform(get("/api/v1/mvp/requests/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().string(hidden));
        verify(service, times(1)).get(ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void helpdeskUncertainCommitRemains503AndNotRetried() throws Exception {
        HelpdeskService service = mock(HelpdeskService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HelpdeskController(service))
                .setControllerAdvice(new HelpdeskExceptionHandler()).build();
        when(service.get(ID)).thenThrow(new WorkflowException(WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN));

        mvc.perform(get("/api/v1/mvp/requests/{id}", ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMIT_OUTCOME_UNKNOWN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("do not automatically retry")));
        verify(service, times(1)).get(ID);
        verifyNoMoreInteractions(service);
    }
}
