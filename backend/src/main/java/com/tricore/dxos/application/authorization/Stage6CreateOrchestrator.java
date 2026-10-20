package com.tricore.dxos.application.authorization;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowPersistencePort;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;

/**
 * Future Stage 6 CREATE application composition, deliberately NOT registered as a Spring bean.
 * Uses Yến's unchanged CurrentPrincipal via Stage6CreateGate: no browser-supplied actor, tenant or ID.
 * Does NOT use the legacy WorkflowRuntime.createInstance duplicate-ID lookup.
 *
 * The caller-owned port factory must return a private, single-operation, target-bound
 * WorkflowPersistencePort. Its PostgreSQL implementation MUST independently authorize
 * loadDefinition and recheck current tenant/ACL/CREATE binding under the tenant guard
 * on createInstance before confirmed durable COMMIT. This class alone is not that proof.
 * Do not expose via HTTP until the physical port and security review pass.
 */
final class Stage6CreateOrchestrator {
    @FunctionalInterface
    interface ScopedPersistenceFactory {
        WorkflowPersistencePort forCreate(AuthenticatedPrincipal verifiedCaller, String serverInstanceId,
                                          ResourceReference resource, String definitionId, long version);
    }

    private final Stage6CreateGate gate;
    private final ScopedPersistenceFactory factory;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    Stage6CreateOrchestrator(Stage6CreateGate gate, ScopedPersistenceFactory factory, Clock clock) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    WorkflowInstance create(ResourceReference resource, String definitionId, long version) {
        // CLASSIFY visibility and authority first; check exact CREATE binding only afterwards.
        AuthenticatedPrincipal caller = gate.check(resource, definitionId, version);
        // 24 bytes = 192 bits; generated exclusively after verified, authorized preliminary check.
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String instanceId = "wf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        WorkflowPersistencePort port = Objects.requireNonNull(
                factory.forCreate(caller, instanceId, resource, definitionId, version), "scoped port");
        // Must perform its OWN private, guarded MATERIALIZE authorization in this port.
        WorkflowDefinition definition = Objects.requireNonNull(
                port.loadDefinition(definitionId, version), "authoritative definition");
        if (!definitionId.equals(definition.definitionId()) || version != definition.definitionVersion()) {
            throw new WorkflowAccessException(WorkflowAccessDecision.UNAVAILABLE);
        }
        WorkflowInstance initial = WorkflowInstance.notStarted(instanceId, definition, resource, clock.instant());
        // The port must recheck live privileges after taking the guard and confirm COMMIT.
        // No pre-insert loadInstance / duplicate-ID collision probe.
        WorkflowInstance result = port.createInstance(initial);
        // A malformed response after the write call cannot establish a safe rollback.
        // Do not report success or blindly repeat CREATE: its COMMIT may be durable.
        if (!sameSnapshot(initial, result)) {
            throw new WorkflowException(WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN);
        }
        return result;
    }

    private static boolean sameSnapshot(WorkflowInstance expected, WorkflowInstance actual) {
        return actual != null
                && expected.instanceId().equals(actual.instanceId())
                && expected.definitionId().equals(actual.definitionId())
                && expected.definitionVersion() == actual.definitionVersion()
                && expected.resourceReference().equals(actual.resourceReference())
                && expected.currentState().equals(actual.currentState())
                && expected.runtimeVersion() == actual.runtimeVersion()
                && expected.lifecycle() == actual.lifecycle()
                && expected.createdAt().equals(actual.createdAt())
                && expected.updatedAt().equals(actual.updatedAt());
    }
}
