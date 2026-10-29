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
}

/**
 * Injected function only: no fetch implementation, host, credentials or LIVE flag.
 * Resolve ALL HTTP responses, including non-2xx; reject only if no usable response
 * was obtained. One invocation must mean one attempt, with no automatic retry,
 * redirect replay, token-refresh replay or write reconciliation.
 */
export type HelpdeskHttpTransport = (
  request: HelpdeskHttpRequest,
) => Promise<HelpdeskHttpResponse>;
