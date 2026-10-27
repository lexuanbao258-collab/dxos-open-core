/** Wire records from PR #63, exact review SHA below. Not Portal/MOCK models. */
export const HELPDESK_CONTRACT_HEAD =
  "e12d105c89400a5fd69dd6cc1fca670ff1f04f51" as const;

export interface HelpdeskCreateInput {
  readonly title: string;
  readonly description: string;
}

export interface HelpdeskVersionInput {
  readonly expectedVersion: number;
}

export interface HelpdeskAssignInput {
  readonly assigneeId: string;
  readonly expectedAssignmentVersion: number;
}

export interface HelpdeskResolveInput extends HelpdeskVersionInput {
  readonly resolution: string;
}

export interface HelpdeskListInput {
  readonly after?: string;
  readonly limit?: number;
}

export interface HelpdeskAssigneeOption {
  readonly id: string;
  readonly displayName: string | null;
}

/** HelpdeskService.RequestView; preserve missing, null and empty detail fields. */
export interface HelpdeskRequest {
  readonly id: string;
  readonly title: string;
  readonly description: string;
  readonly requestType: "IT_HELPDESK";
  readonly requesterId: string;
  readonly assigneeId: string | null;
  readonly resolution: string | null;
  readonly assignmentVersion: number;
  readonly workflowInstanceId: string;
  readonly state: string;
  readonly version: number;
  /** Backend HTTP capability names, including assign/start; never inferred. */
  readonly allowedActions: readonly string[];
  readonly requesterDisplayName?: string | null;
  readonly assigneeDisplayName?: string | null;
  readonly eligibleAssignees?: readonly HelpdeskAssigneeOption[] | null;
}

export interface HelpdeskPage {
  readonly items: readonly HelpdeskRequest[];
  readonly nextCursor: string | null;
}

/** No synthetic id, actor, note or title is added to the authorized projection. */
export interface HelpdeskHistoryEntry {
  readonly transitionId: string;
  readonly sourceState: string;
  readonly targetState: string;
  readonly version: number;
  /** Java Instant: preserve Jackson ISO text/numeric output without Date coercion. */
  readonly occurredAt: string | number;
}
