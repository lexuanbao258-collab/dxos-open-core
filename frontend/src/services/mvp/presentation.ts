import type { ServiceRequest } from "../../domain/request";
import { projectAuthorizedHistory } from "./projections";
import type {
  Assignee,
  AuthorizedHistory,
  BusinessVersion,
  EligibleAssignees,
  ProjectionCompleteness,
  RequestList,
  RequestSnapshot,
  ServerAllowedAction,
  WorkflowVersion,
} from "./types";

type SharedDisplayFields = Pick<
  ServiceRequest,
  "id" | "title" | "description" | "kind" | "priority" | "location"
>;
type OptionalDisplayFields = Partial<
  Pick<ServiceRequest, "requester" | "department" | "createdAt" | "updatedAt">
>;

/**
 * CANDIDATE internal presentation projection of already-normalized data.
 * Shared display fields align with Portal; this is deliberately NOT a
 * ServiceRequest, wire DTO, state mapper, validator or RequestService adapter.
 * No Backend state -> MOCK status or opaque version -> number conversion exists.
 */
export type MvpPortalRequestPresentation = Readonly<
  SharedDisplayFields &
    OptionalDisplayFields & {
      state: string;
      workflowVersion: WorkflowVersion;
      businessVersion?: BusinessVersion;
      allowedActions: readonly ServerAllowedAction[];
      currentAssignee: Assignee | null;
      assigneeDisplayName?: string;
      history?: AuthorizedHistory;
      eligibleAssignees?: EligibleAssignees;
    }
>;

export interface MvpPortalListPresentation {
  readonly items: readonly MvpPortalRequestPresentation[];
  readonly completeness: ProjectionCompleteness;
}

/**
 * Explicit normalized enrichment from an authorized detail projection, NOT an
 * assumed JSON field/envelope. RequestSnapshot currently has no eligibility
 * member. Its approved DTO/port mapping remains pending. Missing enrichment
 * never triggers another request or implies assignment permission.
 */
export interface AuthorizedDetailPresentationData {
  readonly eligibleAssignees?: EligibleAssignees;
}

function copyAssignee(assignee: Assignee): Assignee {
  return {
    id: assignee.id,
    ...(assignee.displayName === undefined
      ? {}
      : { displayName: assignee.displayName }),
  };
}

/** Preserve opaque assignee IDs and completeness; eligibility is not authority. */
export function mapMvpEligibleAssignees(
  eligible: EligibleAssignees,
): EligibleAssignees {
  return {
    items: eligible.items.map(copyAssignee),
    completeness: eligible.completeness,
  };
}

/**
 * Pure read projection. No role/state permission rules, placeholders, identity
 * invention, action aliases, write payloads or automatic history/eligibility
 * fetches. Opaque versions/references retain their original identity and value.
 */
export function mapMvpRequestPresentation(
  snapshot: RequestSnapshot,
  detailData: AuthorizedDetailPresentationData = {},
): MvpPortalRequestPresentation {
  const details = snapshot.details;
  return {
    id: snapshot.id,
    title: details.title,
    description: details.description,
    kind: details.kind,
    priority: details.priority,
    location: details.location,
    ...(details.requester === undefined
      ? {}
      : { requester: details.requester }),
    ...(details.department === undefined
      ? {}
      : { department: details.department }),
    ...(details.createdAt === undefined
      ? {}
      : { createdAt: details.createdAt }),
    ...(details.updatedAt === undefined
      ? {}
      : { updatedAt: details.updatedAt }),
    state: snapshot.workflow.state,
    workflowVersion: snapshot.workflow.version,
    ...(snapshot.businessVersion === undefined
      ? {}
      : { businessVersion: snapshot.businessVersion }),
    allowedActions: snapshot.allowedActions.map((action) => ({
      name: action.name,
      reference: action.reference,
    })),
    currentAssignee:
      snapshot.currentAssignee === null
        ? null
        : copyAssignee(snapshot.currentAssignee),
    ...(snapshot.currentAssignee?.displayName == null
      ? {}
      : { assigneeDisplayName: snapshot.currentAssignee.displayName }),
    ...(snapshot.history === undefined
      ? {}
      : { history: projectAuthorizedHistory(snapshot.history) }),
    ...(detailData.eligibleAssignees === undefined
      ? {}
      : {
          eligibleAssignees: mapMvpEligibleAssignees(
            detailData.eligibleAssignees,
          ),
        }),
  };
}

/** Never treats a partial/unknown list as the complete Portal collection. */
export function mapMvpListPresentation(
  list: RequestList,
): MvpPortalListPresentation {
  return {
    items: list.items.map((snapshot) => mapMvpRequestPresentation(snapshot)),
    completeness: list.completeness,
  };
}
