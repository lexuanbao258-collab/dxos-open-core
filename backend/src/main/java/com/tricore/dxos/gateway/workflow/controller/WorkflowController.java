package com.tricore.dxos.gateway.workflow.controller;

import com.tricore.dxos.core.workflow.ActorReference;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.TransitionCommand;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.gateway.workflow.dto.ActivateWorkflowInstanceRequest;
import com.tricore.dxos.gateway.workflow.dto.CreateWorkflowInstanceRequest;
import com.tricore.dxos.gateway.workflow.dto.ExecuteWorkflowTransitionRequest;
import com.tricore.dxos.gateway.workflow.dto.WorkflowHistoryResponse;
import com.tricore.dxos.gateway.workflow.dto.WorkflowInstanceResponse;
import com.tricore.dxos.gateway.workflow.dto.WorkflowTransitionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Independently testable HTTP adapter, deliberately not a Spring component or registered controller.
 * Production registration is blocked on Yen's verified principal/authorization integration and
 * Anh's adapter/Clock wiring. Every route must be protected before registration.
 * The reserved actor attribute must be supplied server-side after verification and authorization;
 * its presence alone is not proof of authentication. No production producer is implemented here.
 */
@RequestMapping("/api/v1/workflow-instances")
@ResponseBody
public class WorkflowController {
    public static final String TRUSTED_ACTOR_ATTRIBUTE = "workflow.verifiedActor";

    private final WorkflowRuntime runtime;

    public WorkflowController(WorkflowRuntime runtime) {
        this.runtime = runtime;
    }

    @PostMapping
    public ResponseEntity<WorkflowInstanceResponse> create(@Valid @RequestBody CreateWorkflowInstanceRequest input) {
        WorkflowInstanceResponse response = WorkflowInstanceResponse.from(runtime.createInstance(
                input.instanceId(), input.definitionId(), input.definitionVersion(),
                new ResourceReference(input.resourceType(), input.resourceId())));
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/v1/workflow-instances")
                .pathSegment(response.instanceId()).build().encode().toUri()).body(response);
    }

    @PostMapping("/{instanceId}/activate")
    public WorkflowInstanceResponse activate(
            @PathVariable @Pattern(regexp = CreateWorkflowInstanceRequest.INSTANCE_ID_PATTERN) String instanceId,
            @RequestBody(required = false) ActivateWorkflowInstanceRequest input) {
        return WorkflowInstanceResponse.from(runtime.activateInstance(instanceId));
    }

    @PostMapping("/{instanceId}/transitions")
    public WorkflowTransitionResponse transition(
            @PathVariable @Pattern(regexp = CreateWorkflowInstanceRequest.INSTANCE_ID_PATTERN) String instanceId,
            @Valid @RequestBody ExecuteWorkflowTransitionRequest input,
            @RequestAttribute(TRUSTED_ACTOR_ATTRIBUTE) ActorReference actor) {
        return WorkflowTransitionResponse.from(runtime.executeTransition(
                new TransitionCommand(instanceId, input.transitionId(), actor, input.expectedVersion())));
    }

    @GetMapping("/{instanceId}")
    public WorkflowInstanceResponse get(
            @PathVariable @Pattern(regexp = CreateWorkflowInstanceRequest.INSTANCE_ID_PATTERN) String instanceId) {
        return WorkflowInstanceResponse.from(runtime.loadInstance(instanceId));
    }

    @GetMapping("/{instanceId}/history")
    public List<WorkflowHistoryResponse> history(
            @PathVariable @Pattern(regexp = CreateWorkflowInstanceRequest.INSTANCE_ID_PATTERN) String instanceId) {
        return runtime.loadHistory(instanceId).stream().map(WorkflowHistoryResponse::from).toList();
    }
}
