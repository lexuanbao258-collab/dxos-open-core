package com.tricore.dxos.gateway.workflow.controller;

import com.tricore.dxos.core.workflow.ActorReference;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.TransitionCommand;
import com.tricore.dxos.core.workflow.TransitionResult;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.core.workflow.WorkflowTransition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WorkflowControllerTest {
    private static final String BASE = "/api/v1/workflow-instances";
    private static final String CREATE = """
            {"instanceId":"instance-1","definitionId":"publication","definitionVersion":7,
             "resourceType":"document","resourceId":"document-1"}
            """;
    private static final String TRANSITION = """
            {"transitionId":"submit","expectedVersion":0}
            """;
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00.123456789Z");
    private static final ActorReference ACTOR = new ActorReference("verified-subject");
    private static final ResourceReference RESOURCE = new ResourceReference("document", "document-1");
    private static final WorkflowDefinition DEFINITION = new WorkflowDefinition("publication", 7, "draft",
            Set.of("draft", "review", "published"),
            Set.of(new WorkflowTransition("submit", "draft", "review"),
                    new WorkflowTransition("publish", "review", "published")));
    private static final Map<WorkflowErrorCode, Integer> STATUSES = Map.of(
            WorkflowErrorCode.INSTANCE_ALREADY_EXISTS, 409,
            WorkflowErrorCode.VERSION_CONFLICT, 409,
            WorkflowErrorCode.DEFINITION_NOT_FOUND, 404,
            WorkflowErrorCode.INSTANCE_NOT_FOUND, 404,
            WorkflowErrorCode.INVALID_TRANSITION, 422,
            WorkflowErrorCode.TRANSITION_NOT_ALLOWED, 409,
            WorkflowErrorCode.INSTANCE_COMPLETED, 409,
            WorkflowErrorCode.OPERATION_FAILURE, 500,
            WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN, 500);

    private final WorkflowRuntime runtime = mock(WorkflowRuntime.class);
    private MockMvc mvc;

    // This registration exists only in tests, with no claim that requestAttr authenticates a caller.
    @RestController
    @Profile("workflow-http-tests")
    static class TestController extends WorkflowController {
        TestController(WorkflowRuntime runtime) {
            super(runtime);
        }
    }

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new TestController(runtime))
                .setControllerAdvice(new WorkflowExceptionHandler()).build();
    }

    @Test
    void createsAnInstanceWithLocationAndExactSnapshotProjection() throws Exception {
        when(runtime.createInstance("instance-1", "publication", 7, RESOURCE)).thenReturn(initial());

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/instance-1"))
                .andExpect(jsonPath("$.instanceId").value("instance-1"))
                .andExpect(jsonPath("$.definitionId").value("publication"))
                .andExpect(jsonPath("$.definitionVersion").value(7))
                .andExpect(jsonPath("$.resourceType").value("document"))
                .andExpect(jsonPath("$.resourceId").value("document-1"))
                .andExpect(jsonPath("$.currentState").value("draft"))
                .andExpect(jsonPath("$.runtimeVersion").value(0))
                .andExpect(jsonPath("$.lifecycle").value("NOT_STARTED"))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()))
                .andExpect(jsonPath("$.updatedAt").value(NOW.toString()));
        verify(runtime).createInstance("instance-1", "publication", 7, RESOURCE);
        verifyNoMoreInteractions(runtime);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}"})
    void activatesWithoutAcceptingStateOrChangingVersion(String body) throws Exception {
        when(runtime.activateInstance("instance-1")).thenReturn(initial().activate(DEFINITION, NOW));

        mvc.perform(post(BASE + "/instance-1/activate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycle").value("ACTIVE"))
                .andExpect(jsonPath("$.runtimeVersion").value(0))
                .andExpect(jsonPath("$.updatedAt").value(NOW.toString()));
        verify(runtime).activateInstance("instance-1");
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void transitionsUsingOnlyTheServerSuppliedActorAndPreservesRecordPrecision() throws Exception {
        TransitionResult result = initial().activate(DEFINITION, NOW).transition(DEFINITION,
                new TransitionCommand("instance-1", "submit", ACTOR, 0), NOW.plusNanos(1));
        when(runtime.executeTransition(any())).thenReturn(result);

        mvc.perform(post(BASE + "/instance-1/transitions").contentType(MediaType.APPLICATION_JSON)
                        .content(TRANSITION).requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR)
                        .header("X-User", "attacker").header("X-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance.currentState").value("review"))
                .andExpect(jsonPath("$.instance.runtimeVersion").value(1))
                .andExpect(jsonPath("$.instance.definitionVersion").value(7))
                .andExpect(jsonPath("$.instance.updatedAt").value(result.record().occurredAt().toString()))
                .andExpect(jsonPath("$.record.actorId").value(ACTOR.actorId()))
                .andExpect(jsonPath("$.record.sourceState").value("draft"))
                .andExpect(jsonPath("$.record.targetState").value("review"))
                .andExpect(jsonPath("$.record.resultingRuntimeVersion").value(1))
                .andExpect(jsonPath("$.record.occurredAt").value(result.record().occurredAt().toString()))
                .andExpect(jsonPath("$.previousInstance").doesNotExist());
        verify(runtime).executeTransition(new TransitionCommand("instance-1", "submit", ACTOR, 0));
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void readsAuthoritativeInstanceAndOrderedHistoryThroughRuntime() throws Exception {
        TransitionResult first = initial().activate(DEFINITION, NOW).transition(DEFINITION,
                new TransitionCommand("instance-1", "submit", ACTOR, 0), NOW);
        TransitionResult second = first.instance().transition(DEFINITION,
                new TransitionCommand("instance-1", "publish", ACTOR, 1), NOW);
        when(runtime.loadInstance("instance-1")).thenReturn(second.instance());
        when(runtime.loadHistory("instance-1")).thenReturn(List.of(first.record(), second.record()));

        mvc.perform(get(BASE + "/instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycle").value("COMPLETED"))
                .andExpect(jsonPath("$.runtimeVersion").value(2));
        mvc.perform(get(BASE + "/instance-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transitionId").value("submit"))
                .andExpect(jsonPath("$[0].resultingRuntimeVersion").value(1))
                .andExpect(jsonPath("$[1].transitionId").value("publish"))
                .andExpect(jsonPath("$[1].resultingRuntimeVersion").value(2));
        verify(runtime).loadInstance("instance-1");
        verify(runtime).loadHistory("instance-1");
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void returnsEmptyHistory() throws Exception {
        when(runtime.loadHistory("instance-1")).thenReturn(List.of());
        mvc.perform(get(BASE + "/instance-1/history"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @EnumSource(WorkflowErrorCode.class)
    void mapsEveryNormalizedErrorWithoutRetryingOrExposingDetails(WorkflowErrorCode code) throws Exception {
        when(runtime.createInstance(any(), any(), anyLong(), any())).thenThrow(new WorkflowException(code));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().is(STATUSES.get(code)))
                .andExpect(jsonPath("$.status").value(STATUSES.get(code)))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist());
        verify(runtime).createInstance("instance-1", "publication", 7, RESOURCE);
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void hidesUnexpectedProviderAndServerClockFailures() throws Exception {
        when(runtime.activateInstance("instance-1"))
                .thenThrow(new IllegalArgumentException("SQL password=secret; server clock went backwards"));
        mvc.perform(post(BASE + "/instance-1/activate"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("SQL"))));
        verify(runtime).activateInstance("instance-1");
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void headersAndBearerStringsCannotSupplyTheReservedActorAttribute() throws Exception {
        mvc.perform(post(BASE + "/instance-1/transitions").contentType(MediaType.APPLICATION_JSON)
                        .content(TRANSITION).header("X-User", "attacker").header("X-Role", "admin")
                        .header("Authorization", "Bearer unverified").header(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, "attacker"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(runtime);
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsInvalidInputBeforeCallingRuntime(String path, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
        verifyNoInteractions(runtime);
    }

    static Stream<Arguments> invalidInputs() {
        Stream<Arguments> creation = Stream.of(
                "{}", "null", CREATE.replace("\"instance-1\"", "\"\""),
                CREATE.replace("\"instance-1\"", "\"bad/id\""),
                CREATE.replace("\"publication\"", "\" \""),
                CREATE.replace("\"document\"", "\"\""),
                CREATE.replace("\"document-1\"", "null"))
                .map(body -> Arguments.of(BASE, body));
        Stream<Arguments> versions = Stream.of("null", "-1", "7.5", "\"7\"", "true", "{}", "9223372036854775808")
                .flatMap(version -> Stream.of(
                        Arguments.of(BASE, CREATE.replace("\"definitionVersion\":7", "\"definitionVersion\":" + version)),
                        Arguments.of(BASE + "/instance-1/transitions", TRANSITION.replace("\"expectedVersion\":0", "\"expectedVersion\":" + version))));
        Stream<Arguments> transitions = Stream.of("{}", "null", TRANSITION.replace("\"submit\"", "\"\""),
                "{\"transitionId\":\"submit\"}").map(body -> Arguments.of(BASE + "/instance-1/transitions", body));
        return Stream.of(creation, versions, transitions).flatMap(stream -> stream);
    }

    @ParameterizedTest
    @MethodSource("unknownFields")
    void rejectsUnknownControlFieldsForEveryWrite(String path, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BODY"));
        verifyNoInteractions(runtime);
    }

    static Stream<Arguments> unknownFields() {
        return Stream.of("actorId", "actorReference", "roles", "lifecycle", "timestamp", "targetState", "createdAt", "updatedAt")
                .flatMap(field -> Stream.of(
                        Arguments.of(BASE, CREATE.stripTrailing().replaceFirst("}$", ",\"" + field + "\":\"client\"}")),
                        Arguments.of(BASE + "/instance-1/activate", "{\"" + field + "\":\"client\"}"),
                        Arguments.of(BASE + "/instance-1/transitions", TRANSITION.stripTrailing().replaceFirst("}$", ",\"" + field + "\":\"client\"}"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/instance-1/activate", "/instance-1/transitions"})
    void rejectsMalformedJsonWithoutExposingParserDetails(String suffix) throws Exception {
        mvc.perform(post(BASE + suffix).contentType(MediaType.APPLICATION_JSON).content("{broken")
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BODY"))
                .andExpect(content().string(not(containsString("broken"))));
        verifyNoInteractions(runtime);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/instance-1/transitions"})
    void requiresCreationAndTransitionBodies(String suffix) throws Exception {
        mvc.perform(post(BASE + suffix).contentType(MediaType.APPLICATION_JSON)
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(runtime);
    }

    @Test
    void rejectsInvalidPathIdentifiersBeforeRuntime() throws Exception {
        mvc.perform(get(BASE + "/{id}", "bad id")).andExpect(status().isBadRequest());
        verifyNoInteractions(runtime);
    }

    @Test
    void rejectsUnsupportedMediaTypeBeforeRuntime() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content(CREATE))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(runtime);
    }

    @Test
    void doesNotCoerceOrRoundLargeIntegerVersions() throws Exception {
        when(runtime.createInstance("instance-1", "publication", Long.MAX_VALUE, RESOURCE))
                .thenThrow(new WorkflowException(WorkflowErrorCode.DEFINITION_NOT_FOUND));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE.replace("\"definitionVersion\":7", "\"definitionVersion\":" + Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        verify(runtime).createInstance("instance-1", "publication", Long.MAX_VALUE, RESOURCE);

        TransitionCommand command = new TransitionCommand("instance-1", "submit", ACTOR, Long.MAX_VALUE);
        when(runtime.executeTransition(command)).thenThrow(new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT));
        mvc.perform(post(BASE + "/instance-1/transitions").contentType(MediaType.APPLICATION_JSON)
                        .content(TRANSITION.replace("\"expectedVersion\":0", "\"expectedVersion\":" + Long.MAX_VALUE))
                        .requestAttr(WorkflowController.TRUSTED_ACTOR_ATTRIBUTE, ACTOR))
                .andExpect(status().isConflict());
        verify(runtime).executeTransition(command);
        verifyNoMoreInteractions(runtime);
    }

    private WorkflowInstance initial() {
        return WorkflowInstance.notStarted("instance-1", DEFINITION, RESOURCE, NOW);
    }
}
