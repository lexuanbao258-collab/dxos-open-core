import type {
  HelpdeskHistoryEntry,
  HelpdeskPage,
  HelpdeskRequest,
} from "./http/types";
import type { Assignee, EligibleAssignees } from "./types";

/**
 * APP-10 projection for the reviewed PR #63 contract. Separate from both wire
 * DTOs and the older normalized/MOCK model: unsupported display fields stay
 * absent. This cannot satisfy the Portal's legacy ServiceRequest interface.
 */
export interface HelpdeskPortalRequestPresentation {
  readonly id: string;
  readonly title: string;
  readonly description: string;
  readonly kind: "it";
  readonly requestType: "IT_HELPDESK";
  readonly workflowInstanceId: string;
  readonly state: string;
  readonly version: number;
  readonly assignmentVersion: number;
  readonly requesterId: string;
  readonly assigneeId: string | null;
  readonly resolution: string | null;
  readonly allowedActions: readonly string[];
  readonly currentAssignee: Assignee | null;
  readonly requesterDisplayName?: string | null;
  readonly assigneeDisplayName?: string | null;
  readonly eligibleAssignees?: EligibleAssignees | null;
}

export interface HelpdeskPortalListPresentation {
  readonly items: readonly HelpdeskPortalRequestPresentation[];
  readonly nextCursor: string | null;
}

export interface HelpdeskPortalHistoryEntry {
  readonly transitionId: string;
  readonly sourceState: string;
  readonly targetState: string;
  readonly version: number;
  readonly occurredAt: string | number;
}

function assignee(id: string, displayName?: string | null): Assignee {
  return {
    id: { scope: "assignee", value: id },
    ...(displayName === undefined ? {} : { displayName }),
  };
}

/** Input is validated by the wire decoder; never infer permissions or names. */
export function mapHelpdeskRequestPresentation(
  source: HelpdeskRequest,
): HelpdeskPortalRequestPresentation {
  return {
    id: source.id,
    title: source.title,
    description: source.description,
    kind: "it",
    requestType: source.requestType,
    workflowInstanceId: source.workflowInstanceId,
    state: source.state,
    version: source.version,
    assignmentVersion: source.assignmentVersion,
    requesterId: source.requesterId,
    assigneeId: source.assigneeId,
    resolution: source.resolution,
    allowedActions: [...source.allowedActions],
    currentAssignee:
      source.assigneeId === null
        ? null
        : assignee(source.assigneeId, source.assigneeDisplayName),
    ...(source.requesterDisplayName === undefined
      ? {}
      : { requesterDisplayName: source.requesterDisplayName }),
    ...(source.assigneeDisplayName === undefined
      ? {}
      : { assigneeDisplayName: source.assigneeDisplayName }),
    ...(source.eligibleAssignees === undefined
      ? {}
      : {
          eligibleAssignees:
            source.eligibleAssignees === null
              ? null
              : {
                  items: source.eligibleAssignees.map((option) =>
                    assignee(option.id, option.displayName),
                  ),
                  // The DTO has no completeness metadata. Do not claim full visibility.
                  completeness: "unknown" as const,
                },
        }),
  };
}

export function mapHelpdeskListPresentation(
  source: HelpdeskPage,
): HelpdeskPortalListPresentation {
  return {
    items: source.items.map(mapHelpdeskRequestPresentation),
    nextCursor: source.nextCursor,
  };
}

/** Only fields actually returned by HistoryView; no actor/note/event ID/title. */
export function mapHelpdeskHistoryPresentation(
  source: readonly HelpdeskHistoryEntry[],
): readonly HelpdeskPortalHistoryEntry[] {
  return source.map((entry) => ({
    transitionId: entry.transitionId,
    sourceState: entry.sourceState,
    targetState: entry.targetState,
    version: entry.version,
    occurredAt: entry.occurredAt,
  }));
}
