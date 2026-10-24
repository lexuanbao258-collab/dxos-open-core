package com.tricore.dxos.application.mvp;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MvpRequestContractTest {
    @Test
    void nineRoutesAreDeclaredButCannotRegisterAsLiveWithoutGuardedService() throws Exception {
        assertThat(MvpRequestController.class.isAnnotationPresent(RestController.class)).isFalse();
        assertThat(MvpRequestController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/mvp/requests");

        assertPost("create", "", MvpRequestDtos.Create.class);
        assertGet("list", "");
        assertGet("detail", "/{id}", UUID.class);
        assertPost("triage", "/{id}/triage", UUID.class, MvpRequestDtos.VersionedAction.class);
        assertPost("assign", "/{id}/assign", UUID.class, MvpRequestDtos.Assign.class);
        assertPost("start", "/{id}/start", UUID.class, MvpRequestDtos.VersionedAction.class);
        assertPost("resolve", "/{id}/resolve", UUID.class, MvpRequestDtos.Resolve.class);
        assertPost("confirm", "/{id}/confirm", UUID.class, MvpRequestDtos.VersionedAction.class);
        assertGet("history", "/{id}/history", UUID.class);
    }

    @Test
    void requestCannotSupplyRequesterOrActorInCreatePayload() {
        String body = """
                {"title":"Helpdesk issue","description":"Laptop does not start",
                 "requestType":"IT","requester":"forged-identity"}
                """;
        assertThatThrownBy(() -> new ObjectMapper().readValue(body, MvpRequestDtos.Create.class))
                .isInstanceOf(JsonMappingException.class);
    }

    @Test
    void assignmentCannotSupplyActorOrWorkflowState() {
        String body = """
                {"assigneeId":"worker-a","expectedAssignmentVersion":1,
                 "actor":"forged-identity"}
                """;
        assertThatThrownBy(() -> new ObjectMapper().readValue(body, MvpRequestDtos.Assign.class))
                .isInstanceOf(JsonMappingException.class);
    }

    @Test
    void workflowAndAssignmentVersionsAreSeparateWireInputs() {
        assertThat(new MvpRequestDtos.VersionedAction(4L).expectedVersion()).isEqualTo(4L);
        assertThat(new MvpRequestDtos.Assign("worker-a", 8L).expectedAssignmentVersion()).isEqualTo(8L);
    }

    @Test
    void adapterDelegatesToOperationsWithoutRewritingBusinessState() {
        MvpRequestOperations guarded = mock(MvpRequestOperations.class);
        MvpRequestController controller = new MvpRequestController(guarded);
        UUID id = UUID.randomUUID();
        MvpRequestDtos.VersionedAction command = new MvpRequestDtos.VersionedAction(3L);
        controller.confirm(id, command);
        verify(guarded).confirm(id, command);
        verifyNoMoreInteractions(guarded);
    }

    private static void assertPost(String method, String path, Class<?>... args) throws Exception {
        String[] mappings = MvpRequestController.class.getDeclaredMethod(method, args)
                .getAnnotation(PostMapping.class).value();
        if (path.isEmpty()) {
            assertThat(mappings).isEmpty();
        } else {
            assertThat(mappings).containsExactly(path);
        }
    }

    private static void assertGet(String method, String path, Class<?>... args) throws Exception {
        String[] mappings = MvpRequestController.class.getDeclaredMethod(method, args)
                .getAnnotation(GetMapping.class).value();
        if (path.isEmpty()) {
            assertThat(mappings).isEmpty();
        } else {
            assertThat(mappings).containsExactly(path);
        }
    }
}
