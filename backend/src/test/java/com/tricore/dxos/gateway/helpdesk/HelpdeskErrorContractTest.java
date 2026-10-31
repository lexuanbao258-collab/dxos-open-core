package com.tricore.dxos.gateway.helpdesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.application.authorization.WorkflowAccessDecision;
import com.tricore.dxos.application.authorization.WorkflowAccessException;
import com.tricore.dxos.common.error.ApiError;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowTransition;
import com.tricore.dxos.core.workflow.TransitionCommand;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.ActorReference;
import com.tricore.dxos.gateway.workflow.controller.WorkflowExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class HelpdeskErrorContractTest {
    private final WorkflowExceptionHandler handler = new WorkflowExceptionHandler();
    private final ObjectMapper json = new ObjectMapper();

    @Test void domainExceptionsKeepTheirExistingStatusAndApiErrorEnvelope() throws Exception {
        for (WorkflowErrorCode code : WorkflowErrorCode.values()) {
            int expected = switch (code) {
                case INSTANCE_ALREADY_EXISTS, VERSION_CONFLICT, TRANSITION_NOT_ALLOWED, INSTANCE_COMPLETED -> 409;
                case DEFINITION_NOT_FOUND, INSTANCE_NOT_FOUND -> 404;
                case INVALID_TRANSITION -> 422;
                case OPERATION_FAILURE, COMMIT_OUTCOME_UNKNOWN -> 500;
            };
            assertEnvelope(handler.workflow(new WorkflowException(code)), expected);
        }
        assertThat(handler.workflow(new WorkflowException(WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN)).getBody().message())
                .contains("do not automatically retry");
    }

    @Test void accessErrorsKeepVisibilityHidingAndUnavailableSemantics() throws Exception {
        for (WorkflowAccessDecision decision : WorkflowAccessDecision.values()) {
            int expected = switch (decision) { case HIDDEN -> 404; case FORBIDDEN -> 403; case ALLOWED, UNAVAILABLE -> 503; };
            assertEnvelope(handler.authorization(new WorkflowAccessException(decision)), expected);
        }
        assertThat(handler.authorization(new WorkflowAccessException(WorkflowAccessDecision.HIDDEN)).getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test void bindingAndFallbackErrorsReallyReturnApiError() throws Exception {
        assertEnvelope(handler.invalidBody(), 400);
        assertEnvelope(handler.invalidParameter(), 400);
        assertEnvelope(handler.unsupportedMediaType(), 415);
        assertEnvelope(handler.missingTrustedActor(), 401);
        assertEnvelope(handler.internalFailure(), 500);
    }

    @Test void aMissingConfiguredTransitionHasTheReal422CallPathWithoutAnyDatabase() throws Exception {
        var definition = new WorkflowDefinition("it-helpdesk", 1, "SUBMITTED", Set.of("SUBMITTED", "TRIAGED"),
                Set.of(new WorkflowTransition("other", "SUBMITTED", "TRIAGED")));
        var instance = WorkflowInstance.notStarted("wf_test", definition,
                new ResourceReference("mvp_request", "request"), Instant.EPOCH).activate(definition, Instant.EPOCH);
        var failure = catchThrowableOfType(() -> instance.transition(definition,
                new TransitionCommand("wf_test", "triage", new ActorReference("actor"), 0), Instant.EPOCH), WorkflowException.class);
        assertThat(failure.code()).isEqualTo(WorkflowErrorCode.INVALID_TRANSITION);
        assertEnvelope(handler.workflow(failure), 422);
    }

    private void assertEnvelope(ResponseEntity<ApiError> response, int expected) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(expected);
        var value = json.readTree(json.writeValueAsBytes(response.getBody()));
        assertThat(value.size()).isEqualTo(4);
        assertThat(value.path("status").asInt()).isEqualTo(expected);
        assertThat(value.path("code").isTextual()).isTrue();
        assertThat(value.path("message").isTextual()).isTrue();
        assertThat(value.path("errors").isObject()).isTrue();
    }
}
