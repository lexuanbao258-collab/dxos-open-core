package com.tricore.dxos.core.workflow;

import com.tricore.dxos.gateway.workflow.controller.WorkflowController;
import com.tricore.dxos.gateway.workflow.controller.WorkflowExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZoneOffset;

import static com.tricore.dxos.core.workflow.WorkflowInstanceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** HTTP/domain integration with a contract fixture, not evidence of PostgreSQL or authentication. */
class WorkflowHttpRuntimeTest {
    private static final String BASE = "/api/v1/workflow-instances";
    private static final WorkflowDefinition DEFINITION = WorkflowDefinitionTest.approval();
    private static final String TRANSITION = "{\"transitionId\":\"submit\",\"expectedVersion\":0}";
    private final InMemoryWorkflowPersistence persistence = spy(new InMemoryWorkflowPersistence());
    private final WorkflowRuntime runtime = new WorkflowRuntime(persistence,
            Clock.fixed(NOW.plusNanos(123), ZoneOffset.UTC));
    private MockMvc mvc;

    @RestController
    @Profile("workflow-http-tests")
    static class TestController extends WorkflowController {
        TestController(WorkflowRuntime runtime) {
            super(runtime);
        }
    }

    @BeforeEach
    void setUp() {
        persistence.provision(DEFINITION);
        persistence.provision(WorkflowDefinitionTest.delivery());
        mvc = standaloneSetup(new TestController(runtime))
                .setControllerAdvice(new WorkflowExceptionHandler()).build();
    }

    @Test
    void executesTwoIndependentDefinitionsWithAuthoritativeReadsAndStableVersions() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(createBody(DEFINITION.definitionId(), 7)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.definitionVersion").value(7));
        mvc.perform(post(BASE + "/instance-1/activate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeVersion").value(0));
        mvc.perform(post(BASE + "/instance-1/transitions").contentType(MediaType.APPLICATION_JSON).content(TRANSITION)
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.instance.currentState").value("review"));
        mvc.perform(post(BASE + "/instance-1/transitions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transitionId\":\"publish\",\"expectedVersion\":1}")
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.instance.lifecycle").value("COMPLETED"));

        WorkflowDefinition delivery = WorkflowDefinitionTest.delivery();
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(delivery.definitionId(), delivery.definitionVersion()).replace("instance-1", "instance-2")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.definitionVersion").value(delivery.definitionVersion()));
        mvc.perform(post(BASE + "/instance-2/activate")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/instance-2/transitions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transitionId\":\"dispatch\",\"expectedVersion\":0}")
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.instance.lifecycle").value("COMPLETED"));
        mvc.perform(get(BASE + "/instance-1/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].resultingRuntimeVersion").value(2));
        mvc.perform(get(BASE + "/instance-2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionVersion").value(delivery.definitionVersion()))
                .andExpect(jsonPath("$.runtimeVersion").value(1))
                .andExpect(jsonPath("$.updatedAt").value(NOW.plusNanos(123).toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "missing-version"})
    void duplicateIdTakesPrecedenceOverMissingDefinitionWithoutOverwrite(String missing) throws Exception {
        WorkflowInstance active = activate();
        TransitionResult stored = runtime.executeTransition(command("submit", 0));
        String definitionId = missing.equals("missing") ? "missing" : DEFINITION.definitionId();
        long version = missing.equals("missing") ? 7 : 8;

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(createBody(definitionId, version)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSTANCE_ALREADY_EXISTS"));
        assertThat(runtime.loadInstance(active.instanceId())).isSameAs(stored.instance());
        assertThat(runtime.loadHistory(active.instanceId())).containsExactly(stored.record());
        verify(persistence, times(1)).createInstance(any());
        verify(persistence, never()).loadDefinition(definitionId, version);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "missing-version"})
    void missingInstanceAndDefinitionReturnsDefinitionNotFoundWithoutWriting(String missing) throws Exception {
        String definitionId = missing.equals("missing") ? "missing" : DEFINITION.definitionId();
        long version = missing.equals("missing") ? 7 : 8;
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(createBody(definitionId, version)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DEFINITION_NOT_FOUND"));
        assertError(() -> runtime.loadInstance("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        verify(persistence, never()).createInstance(any());
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowErrorCode.class, names = {"OPERATION_FAILURE", "COMMIT_OUTCOME_UNKNOWN"})
    void creationOutcomesDoNotRetryOrClaimRollbackForUnknownCommit(WorkflowErrorCode code) throws Exception {
        failWrite(code);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(createBody(DEFINITION.definitionId(), 7)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(header().doesNotExist("Retry-After"));
        verify(persistence, times(1)).createInstance(any());
        if (code == WorkflowErrorCode.OPERATION_FAILURE) {
            assertError(() -> runtime.loadInstance("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
            assertError(() -> runtime.loadHistory("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        } else {
            assertThat(runtime.loadInstance("instance-1").lifecycle()).isEqualTo(WorkflowLifecycle.NOT_STARTED);
            assertThat(runtime.loadHistory("instance-1")).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowErrorCode.class, names = {"OPERATION_FAILURE", "COMMIT_OUTCOME_UNKNOWN"})
    void activationOutcomesPreserveVersionZeroAndNoHistoryWithoutRetry(WorkflowErrorCode code) throws Exception {
        WorkflowInstance initial = runtime.createInstance("instance-1", DEFINITION.definitionId(), 7, RESOURCE);
        failWrite(code);
        mvc.perform(post(BASE + "/instance-1/activate"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(code.name()));
        verify(persistence, times(1)).commitActivation(any(), any());
        WorkflowInstance stored = runtime.loadInstance("instance-1");
        assertThat(stored.runtimeVersion()).isZero();
        assertThat(runtime.loadHistory("instance-1")).isEmpty();
        if (code == WorkflowErrorCode.OPERATION_FAILURE) assertThat(stored).isSameAs(initial);
        else assertThat(stored.lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowErrorCode.class, names = {"OPERATION_FAILURE", "COMMIT_OUTCOME_UNKNOWN"})
    void transitionOutcomesKeepInstanceAndHistoryAtomicWithoutRetry(WorkflowErrorCode code) throws Exception {
        WorkflowInstance active = activate();
        failWrite(code);
        mvc.perform(post(BASE + "/instance-1/transitions").contentType(MediaType.APPLICATION_JSON).content(TRANSITION)
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(code.name()));
        verify(persistence, times(1)).commitTransition(any());
        if (code == WorkflowErrorCode.OPERATION_FAILURE) {
            assertThat(runtime.loadInstance("instance-1")).isSameAs(active);
            assertThat(runtime.loadHistory("instance-1")).isEmpty();
        } else {
            WorkflowInstance stored = runtime.loadInstance("instance-1");
            assertThat(stored.currentState()).isEqualTo("review");
            assertThat(stored.runtimeVersion()).isEqualTo(1);
            assertThat(runtime.loadHistory("instance-1")).containsExactly(
                    new TransitionRecord("instance-1", "submit", "draft", "review", ACTOR, 1, stored.updatedAt()));
        }
    }

    private WorkflowInstance activate() {
        runtime.createInstance("instance-1", DEFINITION.definitionId(), 7, RESOURCE);
        return runtime.activateInstance("instance-1");
    }

    private void failWrite(WorkflowErrorCode code) {
        if (code == WorkflowErrorCode.OPERATION_FAILURE) persistence.failNextCommit();
        else persistence.loseNextCommitResponse();
    }

    private String createBody(String definitionId, long version) {
        return """
                {"instanceId":"instance-1","definitionId":"%s","definitionVersion":%d,
                 "resourceType":"document","resourceId":"document-1"}
                """.formatted(definitionId, version);
    }
}
