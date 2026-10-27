import type {
  AssignmentCommand,
  AssigneeId,
  CreateRequestInput,
  ObservedVersions,
  RequestSnapshot,
  WorkflowActionCommand,
  WorkflowActionName,
} from "./types";

/** Pick existing form fields only. This is not a wire serializer or validator. */
export function createRequestInput(
  input: CreateRequestInput,
): CreateRequestInput {
  return {
    kind: input.kind,
    title: input.title,
    description: input.description,
    priority: input.priority,
    location: input.location,
  };
}

function observedVersions(snapshot: RequestSnapshot): ObservedVersions {
  return {
    workflow: snapshot.workflow.version,
    ...(snapshot.businessVersion === undefined
      ? {}
      : { business: snapshot.businessVersion }),
  };
}

/**
 * Select only an action supplied on this snapshot, preserving its reference.
 * This local check never replaces Backend authorization or freshness checks.
 * Caller-supplied action input is opaque until action field rules are approved.
 */
export function createWorkflowActionCommand(
  snapshot: RequestSnapshot,
  name: WorkflowActionName,
  input: unknown,
): WorkflowActionCommand {
  const action = snapshot.allowedActions.find((item) => item.name === name);
  if (!action) {
    throw new RangeError("Workflow action was not supplied on this snapshot.");
  }
  return {
    kind: "workflow-action",
    requestId: snapshot.id,
    action: { name: action.name, reference: action.reference },
    observedVersions: observedVersions(snapshot),
    input,
  };
}

/** Does not infer assignment authorization from role, state or eligibility. */
export function createAssignmentCommand(
  snapshot: RequestSnapshot,
  assigneeId: AssigneeId,
  input: unknown,
): AssignmentCommand {
  return {
    kind: "assignment",
    requestId: snapshot.id,
    assigneeId,
    observedVersions: observedVersions(snapshot),
    input,
  };
}
