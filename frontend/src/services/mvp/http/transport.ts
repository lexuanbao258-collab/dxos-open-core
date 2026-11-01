/** JSON decoding/authentication belong to a future separately approved transport. */
export interface HelpdeskHttpRequest {
  readonly method: "GET" | "POST";
  /** Relative path only, scoped to the reviewed Helpdesk namespace. */
  readonly path: string;
  readonly headers: Readonly<{
    Accept: "application/json";
    "Content-Type"?: "application/json";
  }>;
  readonly body?: Readonly<Record<string, unknown>>;
}

export interface HelpdeskHttpResponse {
  readonly status: number;
  readonly body?: unknown;
  /** Recheck session ownership at consumption, before decoding or UI callbacks. */
  readonly assertSessionCurrent?: () => void;
}

/**
 * Injected function only: no fetch implementation, host, credentials or LIVE flag.
 * Resolve all current-session HTTP responses, including non-2xx; reject if no
 * usable response remains (including a retired session). One invocation must
 * mean one attempt, with no automatic retry,
 * redirect replay, token-refresh replay or write reconciliation.
 * Local lifecycle rejections use MvpSessionError with explicit fetch-phase
 * evidence. A session-aware response must guard ownership again at consumption.
 */
export type HelpdeskHttpTransport = (
  request: HelpdeskHttpRequest,
) => Promise<HelpdeskHttpResponse>;
