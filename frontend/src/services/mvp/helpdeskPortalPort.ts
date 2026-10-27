import type { HelpdeskRequestService } from "./HelpdeskRequestService";

/** APP-14 port for the exact PR #63 presentation model. No default transport.
 * The older normalized MvpRequestPort remains a separate candidate contract.
 */
export type MvpHelpdeskRequestPort = Pick<
  HelpdeskRequestService,
  | "create"
  | "list"
  | "detail"
  | "triage"
  | "assign"
  | "start"
  | "resolve"
  | "confirm"
  | "history"
>;
