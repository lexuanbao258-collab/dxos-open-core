import type {
  AssignmentCommand,
  AuthorizedHistory,
  CreateRequestInput,
  EligibleAssignees,
  RequestId,
  RequestList,
  RequestSnapshot,
  WorkflowActionCommand,
} from "./types";

/**
 * CANDIDATE Application boundary, deliberately unbound to Portal and HTTP.
 * Implementations return authoritative, authorized projections. Names here do
 * not register endpoints, HTTP methods, JSON fields or JWT authorities.
 *
 * Pagination/filtering, assignment authorization, wire validation, write
 * preconditions and reconciliation remain pending Backend/security approval.
 * No operation may automatically retry a write, including after authentication.
 */
export interface MvpRequestPort {
  create(input: CreateRequestInput): Promise<RequestSnapshot>;
  list(): Promise<RequestList>;
  detail(id: RequestId): Promise<RequestSnapshot>;
  workflowAction(command: WorkflowActionCommand): Promise<RequestSnapshot>;
  assignment(command: AssignmentCommand): Promise<RequestSnapshot>;
  eligibleAssignees(id: RequestId): Promise<EligibleAssignees>;
  history(id: RequestId): Promise<AuthorizedHistory>;
}
