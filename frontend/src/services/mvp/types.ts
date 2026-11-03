/**
 * CANDIDATE normalized Application models, not JSON DTOs or an HTTP contract.
 * No transport or authority is defined here. A future approved adapter must
 * validate wire data before supplying these models; TypeScript is not validation.
 */
export type RequestId = string;
export type RequestKind = "it" | "facility";
export type RequestPriority = "normal" | "high" | "urgent";

/** Business action meanings accepted for IT MVP v0.1, not transition IDs. */
export type WorkflowActionName =
  "triage" | "start_work" | "resolve" | "confirm";

/** Opaque values: no assumptions about numeric counters, ETags or equality. */
export interface WorkflowVersion {
  readonly scope: "workflow";
  readonly value: unknown;
}

export interface BusinessVersion {
  readonly scope: "business";
  readonly value: unknown;
}

export interface WorkflowSnapshot {
  /** Preserve authoritative state, including CONFIRMED; no local state machine. */
  readonly state: string;
  readonly version: WorkflowVersion;
}

/** Reference supplied by an approved mapping; never manufactured from a label. */
export interface AssigneeId {
  readonly scope: "assignee";
  readonly value: unknown;
}

export interface Assignee {
  readonly id: AssigneeId;
  /** A missing/hidden name must never be manufactured from an ID or persona. */
  readonly displayName?: string | null;
}

export interface ServerAllowedAction {
  readonly name: WorkflowActionName;
  /** Opaque backend evidence; not necessarily a transition ID or a wire field. */
  readonly reference: unknown;
}

export interface CreateRequestInput {
  readonly kind: RequestKind;
  readonly title: string;
  readonly description: string;
  readonly priority: RequestPriority;
  readonly location: string;
}

export interface RequestDetails extends CreateRequestInput {
  readonly requester?: string;
  readonly department?: string;
  readonly createdAt?: string;
  readonly updatedAt?: string;
}

/** Missing actor/note stays missing after authorization filtering. */
export interface AuthorizedHistoryEvent {
  readonly id: string;
  readonly at: string;
  readonly title: string;
  readonly actor?: string;
  readonly note?: string;
}

/** Internal completeness marker, not a pagination or recovery protocol. */
export type ProjectionCompleteness = "complete" | "partial" | "unknown";

export interface AuthorizedHistory {
  readonly events: readonly AuthorizedHistoryEvent[];
  readonly completeness: ProjectionCompleteness;
}

export interface RequestSnapshot {
  readonly id: RequestId;
  readonly details: RequestDetails;
  readonly workflow: WorkflowSnapshot;
  readonly businessVersion?: BusinessVersion;
  /** Supplied projection only; never inferred from persona, role or state. */
  readonly allowedActions: readonly ServerAllowedAction[];
  readonly currentAssignee: Assignee | null;
  /** Absent means not supplied, not an empty or fully visible history. */
  readonly history?: AuthorizedHistory;
}

export interface RequestList {
  readonly items: readonly RequestSnapshot[];
  readonly completeness: ProjectionCompleteness;
}

/** Eligibility is not evidence that the caller is authorized to assign. */
export interface EligibleAssignees {
  readonly items: readonly Assignee[];
  readonly completeness: ProjectionCompleteness;
}

/**
 * Evidence observed on a snapshot, not a prescribed concurrency protocol.
 * Backend must confirm which version each command needs and how it is sent.
 */
export interface ObservedVersions {
  readonly workflow: WorkflowVersion;
  readonly business?: BusinessVersion;
}

export interface WorkflowActionCommand {
  readonly kind: "workflow-action";
  readonly requestId: RequestId;
  readonly action: ServerAllowedAction;
  readonly observedVersions: ObservedVersions;
  /** Required fields/guards remain unconfirmed; no generic note rule assumed. */
  readonly input: unknown;
}

export interface AssignmentCommand {
  readonly kind: "assignment";
  readonly requestId: RequestId;
  readonly assigneeId: AssigneeId;
  readonly observedVersions: ObservedVersions;
  readonly input: unknown;
}
